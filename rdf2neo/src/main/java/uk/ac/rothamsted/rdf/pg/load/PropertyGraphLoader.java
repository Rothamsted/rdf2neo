package uk.ac.rothamsted.rdf.pg.load;

/**
 * The Generic interface to load data into a property Graph, after having SPARQL-fetched them from a Jena TDB triple store. 
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>12 Jan 2018</dd></dl>
 *
 * Modifified by cbobed for refactoring purposes  
 * <dl><dt>Date:</dt><dd>28 Apr 2020</dd></dl>
 */
public interface PropertyGraphLoader
{
	/**
	 * Loads data into the configured format (neo4j server or graphML file for the time being). 
	 * 
	 * @param opts is a generic way to pass implementation-specific options 
	 */
	public void load ( String tdbPath, Object... opts );
}