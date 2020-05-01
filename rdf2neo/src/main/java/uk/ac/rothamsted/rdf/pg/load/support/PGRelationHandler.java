package uk.ac.rothamsted.rdf.pg.load.support;


import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import uk.ac.rothamsted.rdf.pg.load.support.neo4j.CyNodeLoadingHandler;
import uk.ac.rothamsted.rdf.pg.load.support.neo4j.CyNodeLoadingProcessor;
import uk.ac.rothamsted.rdf.pg.load.support.neo4j.CyRelationLoadingProcessor;

public abstract class PGRelationHandler extends PGEntityHandler<QuerySolution> {
	
	protected String relationTypesSparql, relationPropsSparql;

	
	/**
	 * <p>A SPARQL that must return the variables: ?iri ?type ?fromIri ?toIri and distinct result rows (whether you use 
	 * DISTINCT or not).</p>
	 * 
	 * <p>This maps to Cypher relations. fromIri and toIri must correspond to nodes defined by 
	 * {@link CyNodeLoadingProcessor#getNodeIrisSparql()}, so that such nodes, previously inserted in the Neo4J), can be 
	 * matched and linked by the relation.</p>
	 * 
	 * <p>Each relation has an ?iri identifier.
	 * For <a href = 'https://www.w3.org/TR/swbp-n-aryRelations/'>reified RDF relations</a>, this is typically the IRI of 
	 * the reified relation, for plain RDF triples, this is a fictitious IRI, which is typically built by joining and 
	 * hashing the triple subject/predicate/object IRIs (see examples in the src/test/resources).</p> 
	 * 
	 * <p>This is used both by {@link CyRelationLoadingProcessor}, to fetch all relations and their basic 
	 * properties (there must be one type per relation), and by this handler, to fetch elements like a relation 
	 * end points (from/to IRIs) and type, after the ?iri variable is bound to some specific IRI.</p> 
	 * 
	 */
	public String getRelationTypesSparql ()
	{
		return relationTypesSparql;
	}

	@Autowired	( required = false ) @Qualifier ( "relationTypesSparql" )
	public void setRelationTypesSparql ( String relationTypesSparql )
	{
		this.relationTypesSparql = relationTypesSparql;
	}

	/**
	 * <p>This is similar to {@link CyNodeLoadingHandler#getNodePropsSparql()}, it is a SPARQL query that must contain
	 * the variables ?name ?value in the result and the ?iri variable in the WHERE clause. The latter is instantiated
	 * with a specific relation IRI, to get the relation properties.</p> 
	 *
	 * <p>Because of the nature of RDF, this query will typically return properties for reified relations.</p>
	 */
	public String getRelationPropsSparql ()
	{
		return relationPropsSparql;
	}

	@Autowired ( required = false ) @Qualifier ( "relationPropsSparql" )
	public void setRelationPropsSparql ( String relationPropsSparql )
	{
		this.relationPropsSparql = relationPropsSparql;
	}
}