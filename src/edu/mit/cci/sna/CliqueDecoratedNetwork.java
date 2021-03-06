package edu.mit.cci.sna;

import java.util.Collection;
import java.util.Set;

/**
 * User: jintrone
 * Date: 10/3/12
 * Time: 10:57 PM
 */
public interface CliqueDecoratedNetwork extends Network {

    public Set<Clique> getCliques();


    public void addCliques(Collection<Clique> cliques);
}
