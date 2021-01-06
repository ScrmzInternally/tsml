package tsml.classifiers.distance_based.utils.stats.scoring;

import java.util.List;

public interface SplitScorer {

    <A> double score(Labels<A> parentLabels, List<Labels<A>> childLabels);
    
}