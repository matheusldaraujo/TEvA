package edu.mit.cci.teva.engine;

import edu.mit.cci.sna.Network;
import edu.mit.cci.sna.NetworkUtils;
import edu.mit.cci.teva.cpm.cfinder.CFinderCommunityFinder;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: jintrone
 * Date: 7/24/12
 * Time: 6:18 AM
 */
public class BasicStepStrategy implements EvolutionStepStrategy {


    private static Logger log = Logger.getLogger(BasicStepStrategy.class);

    private CommunityModel model;
    private TevaParameters params;


    public BasicStepStrategy(CommunityModel model, TevaParameters parameters) {
        this.model = model;
        this.params = parameters;

    }

    public void setModel(CommunityModel model) {
        this.model = model;
    }

    public void processStep(int i, List<CommunityFrame> fromcliques, List<Network> mergedcliques, List<CommunityFrame> tocliques) {
        log.debug(i + ". From: " + fromcliques);
        log.debug(i + ". Thru: " + mergedcliques.size() + " merged cliques");
        log.debug(i + ". To: " + tocliques);
        if (fromcliques == null && tocliques == null) {
            log.debug(i + ". NO COMMUNITIES FOR WINDOW, skipping...");
            return;
        }
        List<ScoredCommunityFramePair> evolution = new ArrayList<ScoredCommunityFramePair>();

        Set<CommunityFrame> accounting = new HashSet<CommunityFrame>();
        for (Network vinfo : mergedcliques) {
            log.debug("Processing Window:" + i + " Merged communities:" + NetworkUtils.simpleString(vinfo));

            List<CommunityFrame> f = new ArrayList<CommunityFrame>();
            List<CommunityFrame> t = new ArrayList<CommunityFrame>();

            if (fromcliques != null) {
                for (CommunityFrame info : fromcliques) {

                    if (vinfo.getEdges().containsAll(info.getEdges())) {
                        f.add(info);
                        accounting.add(info);
                        log.info("Adding FROM as candidate: " + NetworkUtils.simpleString(info));
                    } else {

                    }
                }
            }


            if (tocliques != null) {
                for (CommunityFrame info : tocliques) {

                    if (vinfo.getEdges().containsAll(info.getEdges())) {
                        t.add(info);
                        accounting.add(info);
                        log.info("Adding TO as candidate: " + NetworkUtils.simpleString(info));
                    } else {


                    }
                }
            }


            if (t.isEmpty()) {
                log.debug(i + ". NO TARGETS " + f);
                for (CommunityFrame info1 : f) {
                    evolution.add(new ScoredCommunityFramePair(info1, CommunityFrame.EMPTY, params.getScoringMethod().score(info1, CommunityFrame.EMPTY)));
                }


            } else if (f.isEmpty()) {
                log.debug(i + ". NO SOURCES " + t);
                for (CommunityFrame info1 : t) {
                    evolution.add(new ScoredCommunityFramePair(CommunityFrame.EMPTY, info1, params.getScoringMethod().score(CommunityFrame.EMPTY, info1)));
                }

            } else {

                for (CommunityFrame info1 : f) {
                    for (CommunityFrame info2 : t) {
                        evolution.add(new ScoredCommunityFramePair(info1, info2, params.getScoringMethod().score(info1, info2)));
                    }
                }
            }

        }
        if (!accounting.containsAll(tocliques)) {
            Set<CommunityFrame> test = new HashSet<CommunityFrame>(accounting);
            test.removeAll(tocliques);

            throw new RuntimeException("To cliques should all be accounted for, there is a problem");
        }

        Collections.sort(evolution, new Comparator<ScoredCommunityFramePair>() {
            public int compare(ScoredCommunityFramePair scoredCliquePair, ScoredCommunityFramePair scoredCliquePair1) {
                if (scoredCliquePair.getScore() < scoredCliquePair1.getScore()) return 1;
                else if (scoredCliquePair.getScore() > scoredCliquePair1.getScore()) return -1;
                else return 0;
            }
        });
        log.debug(i + " cliques to assign:" + accounting);
        log.info(i + ". VIABLE PAIRS: " + evolution);

        for (ScoredCommunityFramePair pair : evolution) {

            //first clique not added, so add pair
            log.debug(i + ". Processing pair: " + pair);
            if (pair.getPair()[1] == CommunityFrame.EMPTY) {
                if (pair.getPair()[0].getCommunity() == null) {
                    addCommunityForFrame(pair.getPair()[0]);
                    log.warn(i + ". ADDING SPURIOUS COMMUNITY with no children (SHOULDN\'T BE HERE!) " + pair.getPair()[0].getCommunity());
                } else {
                    log.debug("NO MATCH FOR " + pair.getPair()[0].getCommunity());
                }

                accounting.remove(pair.getPair()[0]);


            } else if (pair.getPair()[0] == CommunityFrame.EMPTY) {
                addCommunityForFrame(pair.getPair()[1]);
                accounting.remove(pair.getPair()[1]);
                log.info(i + ". CREATE NEW COMMUNITY (NO PARENTS) " + pair.getPair()[1].getCommunity() + "->" + pair.getPair()[1].getNodes());


            } else if (accounting.contains(pair.getPair()[0])) {
                if (pair.getPair()[0].getCommunity() == null) {
                    addCommunityForFrame(pair.getPair()[0]);
                    log.warn(i + ". CREATE COMMUNITY (SHOULDN\'T BE HERE!): " + pair.getPair()[0].getCommunity());
                }

                //placement of this line will determine the policy regarding progeny
                int size = accounting.size();
                accounting.remove(pair.getPair()[0]);
                if (accounting.size() == size) {
                    log.warn("Did not remove frame from accounting: "+pair.getPair()[0]);
                }

                //pair.getPair()[0].getCommunity().setBin(i, pair.getPair()[0]);
                //logProcess.debug(i + ". Set data:" + pair.getPair()[0].getCommunity().getName() + pair.getPair()[0].nodes);
                //have not yet encountered second clique, so just add it, and add the members to the next step
                //in the community
                if (accounting.contains(pair.getPair()[1])) {
                    pair.getPair()[0].getCommunity().addFrame(pair.getPair()[1]);
                    log.debug(i + ". Evolve community: " + pair.getPair()[0].getCommunity().getName() + ":" + pair.getPair()[1]);
                    //  STOPPED
                    size = accounting.size();
                    accounting.remove(pair.getPair()[1]);
                    if (size == accounting.size()) {
                        log.warn("Accounting did not remove a frame!");
                    }

                } else {
                    //have already encountered the second clique, as part of another community, so expire the first of the pair
                    //logProcess.debug(i + ". Already assigned to clique:" + pair.getPair()[1]);
                    log.debug(i + ". Expire " + pair.getPair()[0]);
                    log.debug(i + ". SET PROGENITOR of " + pair.getPair()[1].getCommunity() + " to " + pair.getPair()[0].getCommunity() + " at " + i);
                    if (pair.getPair()[1].getCommunity().getId().equals(pair.getPair()[0].getCommunity().getId())) {
                        log.warn("Community shouldn't consume itself!");
                    }
                    model.addConnection(i, NetworkUtils.coverage(pair.getPair()[0], pair.getPair()[1]), CommunityModel.ConnectionType.CONSUMES, pair.getPair()[0].getCommunity(), pair.getPair()[1].getCommunity());


                    if (params.expireConsumedCommunities()) {
                        log.debug(i + "Expire:" + pair.getPair()[0].getCommunity() + " becomes " + pair.getPair()[1]);
                        pair.getPair()[0].getCommunity().expire();
                    }

                }


            }

            //we've already identified the evolution for the first community, but not second
            //second community is "born"
            else if (accounting.contains(pair.getPair()[1])) {

//                logProcess.debug(i + ". Already processed from-clique " + pair.getPair()[0]);
//                logProcess.debug(i + ". Processing to-clique: " + pair.getPair()[1]);
                accounting.remove(pair.getPair()[1]);
                if (pair.getPair()[1].getCommunity() == null) {
                    //this clause would allows us to determine that this pairing, even though it is not as good a pairing should
                    //still be allowed - merges results
//                    if (pair.getScore() >= params.getScoreForMerge()) {
//                        log.debug(i + ". REMERGE clique " + pair.getPair()[1] + " to mapped community " + pair.getPair()[0].getCommunity());
//                        //for bookeeping purposes in case we encounter another clique that would be consumed by this community.
//                        pair.getPair()[1].getCommunity() = pair.getPair()[0].getCommunity();
//                        pair.getPair()[0].merge(pair.getPair()[1]);
//
//                    } else {

                    addCommunityForFrame(pair.getPair()[1]);
                    log.info(i + ". CREATE COMMUNITY (spawned): " + pair.getPair()[1] + "->" + pair.getPair()[1].getNodes());
                    log.debug(i + ". SET NODES at " + i + " for " + pair.getPair()[1].getCommunity() + pair.getPair()[1].getNodes());
                    model.addConnection(i, NetworkUtils.coverage(pair.getPair()[0], pair.getPair()[1]), CommunityModel.ConnectionType.SPAWNS, pair.getPair()[0].getCommunity(), pair.getPair()[1].getCommunity());
                    log.debug(i + ". SET SPAWNER of " + pair.getPair()[1].getCommunity() + " to " + pair.getPair()[0].getCommunity() + " at " + i);
                    // }
                } else {
                    log.warn("Invalid state - target frame is attached to a community");
                }

            }

            log.debug(i + ". Remaining cliques: " + accounting);

            if (accounting.isEmpty()) {
                break;
            }

        }
    }


    private void addCommunityForFrame(CommunityFrame frame) {
        Community c = Community.create();
        c.addFrame(frame);
        model.addCommunity(c);
    }

    public static void main(String[] args) throws IOException, CommunityFinderException {


        TevaParameters param = new TevaParameters();
        param.setProperty(TevaParameters.WORKING_DIRECTORY, "../REASONTEVA/work");
        BasicStepStrategy step = new BasicStepStrategy(new CommunityModel(param, new Date[][]{}, "TestCorpus"), param);

        CFinderCommunityFinder finder = new CFinderCommunityFinder(false, false, new TevaParameters());
        File one = new File("../REASONTEVA/work/CFinderNetwork.TEvA.0.net");
        File two = new File("../REASONTEVA/work/CFinderNetwork.TEvA.1.net");

        Network none = NetworkUtils.readNetworkFile(one);
        Network ntwo = NetworkUtils.readNetworkFile(two);

        List<CommunityFrame> frames1 = finder.findCommunities(one, 4, 0);
        List<CommunityFrame> frames2 = finder.findCommunities(two, 4, 1);

        FastMergeStrategy strategy = new FastMergeStrategy(4);
        List<Network> result = strategy.process(none, frames1, ntwo, frames2, 0);
        for (Network n : result) {
            log.debug("Network: " + n.getNodes());
        }

        step.processStep(0, frames1, result, frames2);


    }


}
