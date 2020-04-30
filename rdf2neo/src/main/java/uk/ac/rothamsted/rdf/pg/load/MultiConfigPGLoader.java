package uk.ac.rothamsted.rdf.pg.load;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.stereotype.Component;

import uk.ac.rothamsted.rdf.pg.load.support.CyNodeLoadingHandler;
import uk.ac.rothamsted.rdf.pg.load.support.CyNodeLoadingProcessor;
import uk.ac.rothamsted.rdf.pg.load.support.CyRelationLoadingHandler;
import uk.ac.rothamsted.rdf.pg.load.support.CyRelationLoadingProcessor;
import uk.ac.rothamsted.rdf.pg.load.support.GraphMLConfiguration;
import uk.ac.rothamsted.rdf.pg.load.support.GraphMLUtils;
import uk.ac.rothamsted.rdf.pg.support.graphml.GraphMLNodeExportHandler;
import uk.ac.rothamsted.rdf.pg.support.graphml.GraphMLNodeLoadingProcessor;
import uk.ac.rothamsted.rdf.pg.support.graphml.GraphMLRelationExportHandler;
import uk.ac.rothamsted.rdf.pg.support.graphml.GraphMLRelationLoadingProcessor;

/**
 * <H1>The multi-configuration property graph loader.</H1>
 *
 * <p>This uses multiple {@link ConfigItem SPARQL query configurations} to run {@link AbstractSimplePGLoader} multiple times.
 * This allows for logically separate items in an RDF data set to be mapped separately, each with its own set of SPARQL
 * queries.</p> 
 *
 * <p>Note also that everything is designed to support configuration via Spring Bean files.</p>
 *
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>12 Jan 2018</dd></dl>
 *
 * Modifified by cbobed for refactoring purposes  
 * <dl><dt>Date:</dt><dd>28 Apr 2020</dd></dl>
 */
@Component
public class MultiConfigPGLoader implements PropertyGraphLoader, AutoCloseable
{
	private List<ConfigItem> configItems = new LinkedList<> ();
	private ObjectFactory<SimpleCyLoader> cypherLoaderFactory;
	private ObjectFactory<SimpleGraphMLExporter> graphMLExporterFactory; 
	
	private OutputConfig outputConfig; 
	private GraphMLConfiguration graphMLConfiguration; 
	
	private ApplicationContext springContext;
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	private static Logger slog = LoggerFactory.getLogger ( MultiConfigPGLoader.class );
	
	/**
	 * Represents the RDF/Cypher configuration for a single node/relation type.
	 *
	 * @author brandizi
	 * <dl><dt>Date:</dt><dd>20 Jan 2018</dd></dl>
	 *
	 */
	public static class ConfigItem
	{
		private String name;
		
		private String nodeIrisSparql, labelsSparql, nodePropsSparql;
		private String relationTypesSparql, relationPropsSparql;
		private String indexesSparql;
		
		public ConfigItem () {
		}

		public ConfigItem ( 
			String name, 
			String nodeIrisSparql, String labelsSparql, String nodePropsSparql,
			String relationTypesSparql, String relationPropsSparql,
			String indexesSparql
		)
		{
			this.name = name;
			this.nodeIrisSparql = nodeIrisSparql;
			this.labelsSparql = labelsSparql;
			this.nodePropsSparql = nodePropsSparql;
			this.relationTypesSparql = relationTypesSparql;
			this.relationPropsSparql = relationPropsSparql;
			this.indexesSparql = indexesSparql;
		}
		
		public ConfigItem ( 
			String name, 
			String nodeIrisSparql, String labelsSparql, String nodePropsSparql,
			String relationTypesSparql, String relationPropsSparql
		)
		{
			this ( name, nodeIrisSparql, labelsSparql, nodePropsSparql, relationTypesSparql, relationPropsSparql, null );
		}
		
		/**
		 * @see SimpleCyLoader#getName().
		 */
		public String getName () {
			return name;
		}
		public void setName ( String name ) {
			this.name = name;
		}
		
		/**
		 * @see CyNodeLoadingProcessor#getNodeIrisSparql(). 
		 */
		public String getNodeIrisSparql () {
			return nodeIrisSparql;
		}
		public void setNodeIrisSparql ( String nodeIrisSparql ) {
			this.nodeIrisSparql = nodeIrisSparql;
		}

		/**
		 * @see CyNodeLoadingHandler#getLabelsSparql().
		 */
		public String getLabelsSparql () {
			return labelsSparql;
		}
		public void setLabelsSparql ( String labelsSparql ) {
			this.labelsSparql = labelsSparql;
		}
		
		/**
		 * @see CyNodeLoadingHandler#getNodePropsSparql(). 
		 */
		public String getNodePropsSparql () {
			return nodePropsSparql;
		}
		public void setNodePropsSparql ( String nodePropsSparql ) {
			this.nodePropsSparql = nodePropsSparql;
		}
		
		/**
		 * @see CyRelationLoadingHandler#getRelationTypesSparql(). 
		 */
		public String getRelationTypesSparql () {
			return relationTypesSparql;
		}
		public void setRelationTypesSparql ( String relationTypesSparql ) {
			this.relationTypesSparql = relationTypesSparql;
		}
		
		/**
		 * @see CyRelationLoadingHandler#getRelationPropsSparql().
		 */
		public String getRelationPropsSparql () {
			return relationPropsSparql;
		}
		public void setRelationPropsSparql ( String relationPropsSparql ) {
			this.relationPropsSparql = relationPropsSparql;
		}

		/**
		 * @see CypherIndexer#getIndexesSparql(). 
		 */
		public String getIndexesSparql ()
		{
			return indexesSparql;
		}

		public void setIndexesSparql ( String indexesSparql )
		{
			this.indexesSparql = indexesSparql;
		}
	}
	
	
	/** 
	 * Represents the propertyGraph format selector 
	 * @author cbobed
	 * <dl><dt>Date: </dt><dd> 15 Apr 2020</dd></dl>
	 */
	
	public static class OutputConfig {
		public enum GeneratorOutput{
			Cypher, 
			GraphML
		} 
		
		private GeneratorOutput selectedOutput = null; 
		
		public OutputConfig() {
			
		}
		
		public OutputConfig(String config) {
			this.selectedOutput = GeneratorOutput.valueOf(config); 
		}
		public OutputConfig(GeneratorOutput config) {
			this.selectedOutput = config; 
		}

		public GeneratorOutput getSelectedOutput() {
			return selectedOutput;
		}

		public void setSelectedOutput(GeneratorOutput selectedOutput) {
			this.selectedOutput = selectedOutput;
		}
		
	}
	
	/** 
	 * Gets an instance from the Spring application context. The returned instance is bound to the context parameter,
	 * so that {@link MultiConfigPGLoader#close()} can close it.
	 * 
	 * See the XML examples to know how you should configure
	 * beans for this.
	 * 
	 */
	public static MultiConfigPGLoader getSpringInstance ( ApplicationContext beanCtx )
	{
		slog.info ( "Getting Loader configuration from Spring Context" );
		MultiConfigPGLoader mloader = beanCtx.getBean ( MultiConfigPGLoader.class );
		mloader.springContext = beanCtx;
		return mloader;
	}

	/**
	 * Invokes {@link #getSpringInstance(ApplicationContext)} with the context obtained from the XML 
	 * configuration file.
	 * 
	 * The instance returned this way will close the application context when the {@link MultiConfigPGLoader#close()}
	 * method is invoked.
	 *  
	 */
	public static MultiConfigPGLoader getSpringInstance ( String xmlConfigPath )
	{
		slog.info ( "Getting Loader configuration from Spring XML file '{}'", xmlConfigPath );		
		ApplicationContext ctx = new FileSystemXmlApplicationContext ( xmlConfigPath );
		return getSpringInstance ( ctx );
	}
	
	
	/** 
	 * Loops through {@link #getConfigItems() config items} and instantiates a {@link #getCypherLoaderFactory() new simple loader}
	 * for eache item, to load nodes/relations mapped by the config item.
	 */
	@Override
	public void load ( String tdbPath, Object... opts )
	{
		
		log.info("Using {} exporter", outputConfig.getSelectedOutput());
		if (OutputConfig.GeneratorOutput.GraphML.equals(outputConfig.getSelectedOutput())) {
			log.info("GraphML configuration: {}", graphMLConfiguration.printableConfig()); 
		}
		
		switch (getOutputConfig().getSelectedOutput()) {
			case Cypher: 
				// First the nodes ( mode = 0 ) and then the relations ( mode = 1 )
				// That ensures that cross-references made by different queries are taken  
				for ( int mode = 0; mode <= 2; mode++ )
				{
					for ( ConfigItem cfg: this.getConfigItems () )
					{		
						try ( SimpleCyLoader cypherLoader = this.getCypherLoaderFactory ().getObject (); )
						{
							cypherLoader.setName ( cfg.getName () );
							
							CyNodeLoadingProcessor cyNodeLoader = cypherLoader.getCyNodeLoader ();
							CyRelationLoadingProcessor cyRelLoader = cypherLoader.getCyRelationLoader ();
							
							CyNodeLoadingHandler cyNodehandler = (CyNodeLoadingHandler) cyNodeLoader.getBatchJob ();
							CyRelationLoadingHandler cyRelhandler = (CyRelationLoadingHandler) cyRelLoader.getBatchJob ();
							
							cyNodeLoader.setNodeIrisSparql ( cfg.getNodeIrisSparql () );
				
							cyNodehandler.setLabelsSparql ( cfg.getLabelsSparql () );
							cyNodehandler.setNodePropsSparql ( cfg.getNodePropsSparql () );
							
							cyRelhandler.setRelationTypesSparql ( cfg.getRelationTypesSparql () );
							cyRelhandler.setRelationPropsSparql ( cfg.getRelationPropsSparql () );
				
							String indexesSparql = cfg.getIndexesSparql ();
							if ( indexesSparql != null )
								cypherLoader.getCypherIndexer ().setIndexesSparql ( indexesSparql );
							
							cypherLoader.load ( tdbPath, mode == 0, mode == 1, mode == 2 );
						} // try				
					} // for config items
				} // for mode
				break; 
			case GraphML: 
				// I do copy the structure for the time being, I have had to split it as graphML requires 
				// writing the headers and the key elements before the nodes and edges
				
				// no indexing
				for (int mode = 0; mode<=1; mode++) {
					for ( ConfigItem cfg: this.getConfigItems () )
					{		
						try ( SimpleGraphMLExporter graphMLExporter = this.getGraphMLExporterFactory().getObject (); )
						{
							graphMLExporter.setName ( cfg.getName () );
							
							GraphMLNodeLoadingProcessor graphMLNodeLoader = graphMLExporter.getGraphMLNodeLoader();
							GraphMLRelationLoadingProcessor graphMLRelLoader = graphMLExporter.getGraphMLRelationLoader ();
							
							GraphMLNodeExportHandler graphMLNodeExportHandler = (GraphMLNodeExportHandler) graphMLNodeLoader.getBatchJob ();
							GraphMLRelationExportHandler graphMLRelExportHandler = (GraphMLRelationExportHandler) graphMLRelLoader.getBatchJob ();
							
							graphMLNodeLoader.setNodeIrisSparql ( cfg.getNodeIrisSparql () );
				
							graphMLNodeExportHandler.setLabelsSparql ( cfg.getLabelsSparql () );
							graphMLNodeExportHandler.setNodePropsSparql ( cfg.getNodePropsSparql () );
							
							graphMLRelExportHandler.setRelationTypesSparql ( cfg.getRelationTypesSparql () );
							graphMLRelExportHandler.setRelationPropsSparql ( cfg.getRelationPropsSparql () );
				
							graphMLExporter.load ( tdbPath, mode == 0, mode == 1);
						} // try				
					} // for config items
				}
				
				// at this point we should have all the information at
				// - GraphMLNodeExportHandler static class fields 
				// - GraphMLRelationExportHandler static class fields
				// - File GraphMLConfiguration.getOutputFile()+GraphMLConfiguration.NODE_FILE_EXTENSION
				// - File GraphMLConfiguration.getOutputFile()+GraphMLConfiguration.EDGE_FILE_EXTENSION
				// we need to put it together (in posterior versions, instead of relying on the URIs hashcodes, we 
				// should try to store the data in an intermediate storage (Redis is very good candidate for this) 
				// to avoid this final step. 
				
				try (BufferedReader inNodes = Files.newBufferedReader(Paths.get(GraphMLConfiguration.getOutputFile()+GraphMLConfiguration.NODE_FILE_EXTENSION)); 
					BufferedReader inEdges = Files.newBufferedReader(Paths.get(GraphMLConfiguration.getOutputFile()+GraphMLConfiguration.EDGE_FILE_EXTENSION)) ) {
					
					BufferedWriter outGraphMLFile = null; 
					
					if (Files.exists(Paths.get(GraphMLConfiguration.getOutputFile()))) {
						outGraphMLFile = Files.newBufferedWriter(Paths.get(GraphMLConfiguration.getOutputFile()), StandardOpenOption.TRUNCATE_EXISTING); 
					}
					else {
						outGraphMLFile = Files.newBufferedWriter(Paths.get(GraphMLConfiguration.getOutputFile()), StandardOpenOption.CREATE_NEW, StandardOpenOption.APPEND); 
					}
					
					// we preapre the headers and the schema information 
					
					StringBuilder strB = new StringBuilder(); 
					
					strB.append(GraphMLUtils.GRAPHML_TAG_HEADER).append("\n");
					
					for (String nodeAttribute: GraphMLNodeExportHandler.getGatheredNodeProperties()) {
						strB.append(GraphMLUtils.KEY_TAG_START); 
						strB.append(GraphMLUtils.ID_ATTR).append("=\"").append(nodeAttribute).append("\" "); 
						strB.append(GraphMLUtils.FOR_ATTR).append("=\"").append(GraphMLUtils.NODE_FOR_VALUE).append("\" ");
						// for the time being, we don't support typing for the key / data 
						// maybe we could add it via 
						strB.append(GraphMLUtils.ATTR_NAME_ATTR).append("=\"").append(nodeAttribute).append("\" /> \n"); 
					}
					for (String edgeAttribute: GraphMLRelationExportHandler.getGatheredEdgeProperties()) {
						strB.append(GraphMLUtils.KEY_TAG_START); 
						strB.append(GraphMLUtils.ID_ATTR).append("=\"").append(edgeAttribute).append("\" "); 
						strB.append(GraphMLUtils.FOR_ATTR).append("=\"").append(GraphMLUtils.EDGE_FOR_VALUE).append("\" ");
						// for the time being, we don't support typing for the key / data 
						// maybe we could add it via 
						strB.append(GraphMLUtils.ATTR_NAME_ATTR).append("=\"").append(edgeAttribute).append("\" /> \n"); 
					}
					
					strB.append(GraphMLUtils.GRAPH_TAG_START); 
					strB.append(GraphMLUtils.DEFAULT_DIRECTED_ATTR).append("=\"").append(GraphMLUtils.DIRECTED_DEFAULT_DIRECTED_VALUE).append("\" > \n"); 
					
					outGraphMLFile.write(strB.toString());
					
					String element = null;
				    while ((element= inNodes.readLine()) != null) {
				        outGraphMLFile.write(element); 
				        outGraphMLFile.newLine();
				    }
				    while ((element= inEdges.readLine()) != null) {
				    	outGraphMLFile.write(element);
				    	outGraphMLFile.newLine();
				    }
					
				    strB = new StringBuilder(); 
				    strB.append(GraphMLUtils.GRAPH_TAG_END).append("\n").append(GraphMLUtils.GRAPHML_TAG_END); 
				    
				    outGraphMLFile.write(strB.toString());
					outGraphMLFile.flush();
				}
				catch (IOException e) {
					log.error("Problems writing all the data together"); 
					e.printStackTrace();
				}
				
				
				
				break; 
			default: 
				break;
		}
	}

	
	public OutputConfig getOutputConfig() {
		return outputConfig;
	}
	@Resource (name="outputConfig")
	public void setOutputConfig(OutputConfig output) {
		this.outputConfig = output;
	}

	public GraphMLConfiguration getGraphMLConfiguration() {
		return graphMLConfiguration;
	}
	@Resource (name="graphMLConfiguration")
	public void setGraphMLConfiguration(GraphMLConfiguration graphMLConfiguration) {
		this.graphMLConfiguration = graphMLConfiguration;
	}

	/**
	 * A single configuration defines how Cypher nodes and relations are mapped from RDF.
	 * 
	 * @see {@link #load(String, Object...)} 
	 */
	public List<ConfigItem> getConfigItems ()
	{
		return configItems;
	}

	
	@Autowired ( required = false )
	public void setConfigItems ( List<ConfigItem> configItems )
	{
		this.configItems = configItems;
	}

	/**
	 * This is used to get a new {@link SimpleCyLoader} to be used with a new configuration while iterating over
	 * {@link #getConfigItems()}. This is designed this way in order to make it possible to configure/autowire
	 * a factory via Spring.
	 * 
	 */
	public ObjectFactory<SimpleCyLoader> getCypherLoaderFactory ()
	{
		return cypherLoaderFactory;
	}

	@Resource ( name = "simpleCyLoaderFactory" )
	public void setCypherLoaderFactory ( ObjectFactory<SimpleCyLoader> cypherLoaderFactory )
	{
		this.cypherLoaderFactory = cypherLoaderFactory;
	}


	public ObjectFactory<SimpleGraphMLExporter> getGraphMLExporterFactory() 
	{
		return graphMLExporterFactory; 
	}
	@Resource (name = "simpleGraphMLExporterFactory")
	public void setGraphMLExporterFactory ( ObjectFactory<SimpleGraphMLExporter> graphMLExporterFactory)
	{
		this.graphMLExporterFactory = graphMLExporterFactory;
	}

	
	
	/**
	 * This does something effectively if the current loader instance was obtained via one of the 
	 * {@link #getSpringInstance(ApplicationContext)} methods. The corresponding Spring context is closed. If the 
	 * loader was obtained some other way, this method has no effect and you can safely call it, just in case.
	 * 
	 */
	@Override
	public void close ()
	{
		if ( this.springContext == null || ! ( springContext instanceof ConfigurableApplicationContext ) ) return;
		
		ConfigurableApplicationContext cfgCtx = (ConfigurableApplicationContext) springContext;
		cfgCtx.close ();
	}

}