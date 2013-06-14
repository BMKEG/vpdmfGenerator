package edu.isi.bmkeg.vpdmf.bin;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Model;

import utils.VPDMfGeneratorConverters;
import edu.isi.bmkeg.uml.model.UMLmodel;
import edu.isi.bmkeg.uml.sources.UMLModelSimpleParser;
import edu.isi.bmkeg.vpdmf.controller.archiveBuilder.VPDMfArchiveFileBuilder;
import edu.isi.bmkeg.vpdmf.model.definitions.VPDMf;
import edu.isi.bmkeg.vpdmf.model.definitions.specs.VpdmfSpec;
import edu.isi.bmkeg.vpdmf.utils.VPDMfParser;

public class BuildVpdmfMysqlMavenZip {

	public static String USAGE = "arguments: [<proj1> <proj2> ... <projN>] <target-dir>"; 

	private VPDMf top;
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {

		if( args.length < 2) {
			System.err.println(USAGE);
			System.exit(-1);
		}
		
		String group = "";
		String artifactId = "";
		String version = "";
		
		List<File> viewFiles = new ArrayList<File>();
		List<File> dataFiles = new ArrayList<File>();
		List<String> solrViews = new ArrayList<String>();
		UMLmodel model = null;
		
		File firstPom = new File( args[0].replaceAll("\\/$", "") + "/pom.xml" );
		Model firstPomModel = VPDMfGeneratorConverters.readModelFromPom(firstPom);
		VpdmfSpec firstSpecs = VPDMfGeneratorConverters.readVpdmfSpecFromPom(firstPomModel);
		
		List<File> pomFiles = new ArrayList<File>();
		for (int i=0; i<args.length-1; i++) {
			File pomFile = new File(args[i].replaceAll("\\/$", "") + "/pom.xml");	
			pomFiles.add(pomFile);
		}
		
		File zip = new File(args[args.length-1].replaceAll("\\/$", "") + "/" 
				+ firstSpecs.getArtifactId() + "-mysql-" + firstSpecs.getVersion() 
				+ ".zip" );
		
		DistributionManagement dm = VPDMfGeneratorConverters.
				readDistributionManagementFromPom( firstPomModel, firstPom);
		
		if(dm == null) {
			throw new Exception("Can't find distribution management information");
		}
		
		String repoId = "";
		String repoUrl = "";
		if( firstSpecs.getVersion().endsWith("SNAPSHOT") ) {
			repoId = dm.getSnapshotRepository().getId();	
			repoUrl = dm.getSnapshotRepository().getUrl();
		} else {
			repoId = dm.getRepository().getId();	
			repoUrl = dm.getRepository().getUrl();	
		}
		
		Iterator<File> it = pomFiles.iterator();
		while( it.hasNext() ) {
			File pomFile = it.next();
			
			//
			// parse the specs files
			//
			Model pomModel = VPDMfGeneratorConverters.readModelFromPom(pomFile);
			VpdmfSpec vpdmfSpec = VPDMfGeneratorConverters.readVpdmfSpecFromPom(pomModel);
			
			// Model file
			String modelPath = vpdmfSpec.getModel().getPath();
			String modelType = vpdmfSpec.getModel().getType();
			File modelFile = new File(pomFile.getParent() + "/" + modelPath);	

			// View directory
			String viewsPath = vpdmfSpec.getViewsPath();
			File viewsDir = new File(pomFile.getParent() + "/" + viewsPath);	
			viewFiles.addAll( VPDMfParser.getAllSpecFiles(viewsDir) );

			// solr views 
			solrViews.addAll( vpdmfSpec.getSolrViews() );

			// Data file
			File data = null;
			if( vpdmfSpec.getData() != null ) {
				String dataPath = vpdmfSpec.getData().getPath();
				data = new File(pomFile.getParent() + "/" + dataPath);
				if( !data.exists() )
					data = null;
				else 
					dataFiles.add(data);
			}
			
			if( data != null ) 
				System.out.println("Data File: " + data.getPath());
		
			UMLModelSimpleParser p = new UMLModelSimpleParser(UMLmodel.XMI_MAGICDRAW);
			p.parseUMLModelFile(modelFile);
			UMLmodel m = p.getUmlModels().get(0);
			
			if( model == null ) {
				
				group = vpdmfSpec.getGroupId();
				artifactId = vpdmfSpec.getArtifactId();
				version = vpdmfSpec.getVersion();
				model = m;
				
			} else {
				
				model.mergeModel(m);
			
			}
			
		}			
		
		model.checkForProxy();
		
		VPDMfParser vpdmfP = new VPDMfParser();
		VPDMf top = vpdmfP.buildAllViews(firstSpecs, model, viewFiles, solrViews);	
		
		if( firstSpecs.getUimaPackagePattern() != null && firstSpecs.getUimaPackagePattern().length() > 0 ) {
			top.setUimaPkgPattern(firstSpecs.getUimaPackagePattern());
		}
		
		if( zip.exists() ) {
			System.err.println( zip.getPath()+ " already exists. Overwriting old version.");
			zip.delete();
		}
		
		// Supports creating archives in a project 'target' 
		// directory when it hasn't been created yet.
		File zipdir = zip.getParentFile();
		if (!zipdir.exists()) {
			System.err.println("directory " + zipdir.getPath() + " doesn't exist. Creating it");
			zipdir.mkdirs();
		}
		
		VPDMfArchiveFileBuilder vafb = new VPDMfArchiveFileBuilder();
		vafb.buildArchiveFile(firstSpecs, top, dataFiles, zip, repoId, repoUrl);

		System.out.println("MySQL VPDMf archive generated: " + zip.getPath());
		
	}
	

}