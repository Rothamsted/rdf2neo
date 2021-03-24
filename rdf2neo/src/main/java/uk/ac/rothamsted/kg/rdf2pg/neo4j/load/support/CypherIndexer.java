package uk.ac.rothamsted.kg.rdf2pg.neo4j.load.support;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import info.marcobrandizi.rdfutils.jena.TDBEndPointHelper;
import uk.ac.rothamsted.kg.rdf2pg.neo4j.load.SimpleCyLoader;
import uk.ac.rothamsted.kg.rdf2pg.pgmaker.support.rdf.RdfDataManager;

/**
 * Used by {@link SimpleCyLoader} to build Neo4j indices.
 * 
 * The idea is that {@link #getIndexesSparql()} returns a list of {@code <?nodeLabel ?propertyNameToIndex>}. 
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>26 Feb 2018</dd></dl>
 *
 */
@Component @Scope ( scopeName = "pgmakerSession" )
public class CypherIndexer
{
	private Neo4jDataManager neo4jDataManager;
	private RdfDataManager rdfDataManager;
	
	private String indexesSparql;

	private Logger log = LoggerFactory.getLogger ( this.getClass () ); 
	
	public void index ()
	{
		String idxSparql = getIndexesSparql ();
		if ( idxSparql == null ) return;
		
		log.info ( "Starting Cypher Indexing" );
		
		RdfDataManager rdfMgr = this.getRdfDataManager ();
		Neo4jDataManager neoMgr = this.getNeo4jDataManager ();
		
		Function<String, String> labelIdConverter = rdfMgr.getPGNodeLabelIdConverter ();
		Function<String, String> propIdConverter = rdfMgr.getPGPropertyIdConverter ();
		
		final List<String> allLabels = new LinkedList<> ();
		
		rdfMgr.processSelect ( "CypherIndexer", idxSparql, row -> 
		{  
			String label = rdfMgr.getPGId ( row.get ( "label" ), labelIdConverter );
			if ( label == null ) throw new IllegalArgumentException ( "Null label in the indices query" );
			
			List<String> labels;
			
			if ( "*".equals ( label ) )
			{
				// Get all the labels
				if ( allLabels.isEmpty () )
					neoMgr.processCypherMatches ( 
						lrec -> allLabels.add ( lrec.get ( 0 ).asString () ), 
						"call db.labels();" 
					);
			
				labels = allLabels;
			}
			else {
				// Or, just use the one you found
				labels = new LinkedList<> ();
				labels.add ( label );
			}
			
			// And now the property
			String propName = rdfMgr.getPGId ( row.get ( "propertyName" ), propIdConverter );
			if ( propName == null ) throw new IllegalArgumentException ( String.format (  
				"Null property name in the indices query, for the label %s", label 
			));

			for ( String actualLabel: labels ) {
				log.info ( "Indexing on '{}'.'{}'", actualLabel, propName );
				neo4jDataManager.runCypher ( String.format (
					"CREATE INDEX IF NOT EXISTS FOR (n:`%s`) ON (n.`%s`)",						
					actualLabel, propName 
				));
			}
		});

		log.info ( "Cypher Indexing Ended" );		
	}
	
	public Neo4jDataManager getNeo4jDataManager ()
	{
		return neo4jDataManager;
	}

	@Autowired
	public void setNeo4jDataManager ( Neo4jDataManager neo4jDataManager )
	{
		this.neo4jDataManager = neo4jDataManager;
	}
	
	/**
	 * Used internally, to interact with an RDF backend. We don't care about 
	 * {@link TDBEndPointHelper#close() closing} it, so the caller has to do it.
	 */
	public RdfDataManager getRdfDataManager ()
	{
		return rdfDataManager;
	}

	@Autowired
	public void setRdfDataManager ( RdfDataManager rdfDataManager )
	{
		this.rdfDataManager = rdfDataManager;
	}

	/**
	 * See above.
	 */
	public String getIndexesSparql ()
	{
		return indexesSparql;
	}

	@Autowired ( required = false ) @Qualifier ( "indexesSparql" )
	public void setIndexesSparql ( String indexesSparql )
	{
		this.indexesSparql = indexesSparql;
	}
}
