package org.geoserver.data.versioning;

import java.util.Iterator;
import java.util.Set;

import org.geogit.api.GeoGIT;
import org.geogit.api.ObjectId;
import org.geogit.api.Ref;
import org.geogit.repository.Repository;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.identity.ResourceId;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;

public class ResourceIdFeatureCollector implements Iterable<Feature> {

    private final Repository repository;

    private final FeatureType featureType;

    private final Set<ResourceId> resourceIds;

    public ResourceIdFeatureCollector(final Repository repository, final FeatureType featureType,
            final Set<ResourceId> resourceIds) {
        this.repository = repository;
        this.featureType = featureType;
        this.resourceIds = resourceIds;
    }

    @Override
    public Iterator<Feature> iterator() {

        Iterator<Ref> featureRefs = Iterators.emptyIterator();

        GeoGIT ggit = new GeoGIT(repository);
        VersionQuery query = new VersionQuery(ggit, featureType.getName());
        try {
            for (ResourceId rid : resourceIds) {
                Iterator<Ref> ridIterator;
                ridIterator = query.get(rid);
                featureRefs = Iterators.concat(featureRefs, ridIterator);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Iterator<Feature> features = Iterators.transform(featureRefs, new RefToFeature(repository,
                featureType));

        return features;
    }

    private static class RefToFeature implements Function<Ref, Feature> {

        private final Repository repo;

        private final FeatureType type;

        public RefToFeature(final Repository repo, final FeatureType type) {
            this.repo = repo;
            this.type = type;
        }

        @Override
        public Feature apply(final Ref featureRef) {
            String featureId = featureRef.getName();
            ObjectId contentId = featureRef.getObjectId();
            Feature feature = repo.getFeature(type, featureId, contentId);
            return VersionedFeatureWrapper.wrap(feature, featureRef.getObjectId().toString());
        }

    }

}
