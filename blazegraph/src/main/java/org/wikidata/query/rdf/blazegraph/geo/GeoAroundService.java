package org.wikidata.query.rdf.blazegraph.geo;

import org.openrdf.model.URI;
import org.openrdf.model.impl.URIImpl;
import org.wikidata.query.rdf.common.uri.GeoSparql;
import org.wikidata.query.rdf.common.uri.Ontology;

import com.bigdata.rdf.model.BigdataValueFactory;
import com.bigdata.rdf.sparql.ast.ConstantNode;
import com.bigdata.rdf.sparql.ast.DummyConstantNode;
import com.bigdata.rdf.sparql.ast.JoinGroupNode;
import com.bigdata.rdf.sparql.ast.StatementPatternNode;
import com.bigdata.rdf.sparql.ast.TermNode;
import com.bigdata.rdf.sparql.ast.eval.ServiceParams;
import com.bigdata.rdf.sparql.ast.service.ServiceCallCreateParams;
import com.bigdata.rdf.store.AbstractTripleStore;
import com.bigdata.rdf.vocab.Vocabulary;
import com.bigdata.service.geospatial.GeoSpatial;
import com.bigdata.service.geospatial.GeoSpatial.GeoFunction;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Implements a service to do geospatial search.
 *
 * This class searches for items around certain point.
 *
 * Example:
 *
 * SELECT * WHERE {
 *   wd:Q90 wdt:P625 ?parisLoc .
 *
 *   SERVICE wikibase:around {
 *     ?place wdt:P625 ?location .
 *     bd:serviceParam wikibase:center ?parisLoc .
 *     bd:serviceParam wikibase:radius "1" .
 *     bd:serviceParam wikibase:distance ?distance .
 *   }
 * }
 */
@SuppressFBWarnings(value = "FCCD_FIND_CLASS_CIRCULAR_DEPENDENCY", justification = "This should probably be fixed at some point")
public class GeoAroundService extends GeoService {

    /**
     * The URI service key.
     */
    public static final URI SERVICE_KEY = new URIImpl(
            Ontology.NAMESPACE + "around");

    /**
     * wikibase:center parameter name.
     */
    public static final URIImpl CENTER_PARAM = new URIImpl(
            Ontology.NAMESPACE + "center");

    /**
     * wikibase:radius parameter name.
     */
    public static final URIImpl RADIUS_PARAM = new URIImpl(
            Ontology.NAMESPACE + "radius");

    /**
     * wikibase:distance parameter name.
     */
    public static final URIImpl DISTANCE_PARAM = new URIImpl(
            Ontology.NAMESPACE + "distance");

    @Override
    protected JoinGroupNode buildServiceNode(ServiceCallCreateParams params,
            ServiceParams serviceParams) {
        final AbstractTripleStore store = params.getTripleStore();
        final Vocabulary voc = store.getVocabulary();
        BigdataValueFactory vf = store.getValueFactory();

        final StatementPatternNode pattern = getPatternNode(params);
        final TermNode searchVar = pattern.s();
        final TermNode predicate = pattern.p();
        final TermNode locationVar = pattern.o();

        final JoinGroupNode newGroup = new JoinGroupNode();
        // ?var geo:search "inCircle" .
        newGroup.addArg(new StatementPatternNode(
                        searchVar,
                        new DummyConstantNode(vf.asValue(GeoSpatial.SEARCH)),
                        new DummyConstantNode(vf.createLiteral(GeoFunction.IN_CIRCLE.toString()))
                ));
        // ?var geo:predicate wdt:P625 .
        newGroup.addArg(new StatementPatternNode(
                        searchVar,
                        new DummyConstantNode(vf.asValue(GeoSpatial.PREDICATE)),
                        predicate
                ));
        // ?var geo:searchDatatype ogc:wktLiteral .
        newGroup.addArg(new StatementPatternNode(
                        searchVar,
                        new DummyConstantNode(vf.asValue(GeoSpatial.SEARCH_DATATYPE)),
                        new ConstantNode(
                                voc.getConstant(new URIImpl(GeoSparql.WKT_LITERAL)))));

        // ?var geo:spatialCircleCenter ?parisLoc .
        newGroup.addArg(new StatementPatternNode(
                        searchVar,
                        new DummyConstantNode(vf.asValue(GeoSpatial.SPATIAL_CIRCLE_CENTER)),
                        getParam(serviceParams, CENTER_PARAM)
                ));
        // ?var geo:spatialCircleRadius "1" .
        newGroup.addArg(new StatementPatternNode(
                        searchVar,
                        new DummyConstantNode(vf.asValue(GeoSpatial.SPATIAL_CIRCLE_RADIUS)),
                        getParam(serviceParams, RADIUS_PARAM)
                ));
        // ?var geo:locationValue ?location .
        newGroup.addArg(new StatementPatternNode(
                        searchVar,
                        new DummyConstantNode(vf.asValue(GeoSpatial.LOCATION_VALUE)),
                        locationVar));
        // ?var geo:coordSystem "0" .
        newGroup.addArg(new StatementPatternNode(
                        searchVar,
                        new DummyConstantNode(vf.asValue(GeoSpatial.COORD_SYSTEM)),
                        getGlobeNode(vf, serviceParams)
                ));
        final TermNode distance = serviceParams.get(DISTANCE_PARAM, null);
        if (distance != null) {
            // ?var geo:distanceValue ?distance .
            newGroup.addArg(new StatementPatternNode(
                            searchVar,
                            new DummyConstantNode(vf.asValue(GeoSpatial.DISTANCE_VALUE)),
                            distance
                    ));
        }

        return newGroup;
    }
}
