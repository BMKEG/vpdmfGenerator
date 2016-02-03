package edu.isi.bmkeg.vpdmf.bin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.maven.model.Model;
import org.semanticweb.owlapi.model.OWLOntology;

import com.google.common.io.Files;

import edu.isi.bmkeg.uml.builders.JavaPojoUmlBuilder;
import edu.isi.bmkeg.uml.builders.OwlUmlBuilder;
import edu.isi.bmkeg.uml.model.UMLclass;
import edu.isi.bmkeg.uml.model.UMLmodel;
import edu.isi.bmkeg.uml.sources.UMLModelSimpleParser;
import edu.isi.bmkeg.uml.utils.OwlAPIUtility;
import edu.isi.bmkeg.utils.Converters;
import edu.isi.bmkeg.vpdmf.model.definitions.specs.VpdmfSpec;
import edu.isi.bmkeg.vpdmf.utils.VPDMfParser;
import utils.VPDMfGeneratorConverters;
import utils.VPDMfModelReader;

public class BuildBasicOwl {

	public static String USAGE = "arguments: [<proj1> <proj2> ... <projN>] <target-dir> <bmkeg-parent-version>"; 
	
	public static Logger logger = Logger
			.getLogger("edu.isi.bmkeg.vpdmf.bin.BuildBasicVpdmfModel");
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {

		if( args.length < 3 ) {
			System.err.println(USAGE);
			System.exit(-1);
		}

		List<File> pomFiles = new ArrayList<File>();
		for (int i = 0; i < args.length - 2; i++) {
			File pomFile = new File(args[i].replaceAll("\\/$", "") + "/pom.xml");	
			pomFiles.add(pomFile);
		}
		File dir = new File(args[args.length - 2]);
		String bmkegParentVersion = args[args.length - 1];

		VPDMfModelReader reader = new VPDMfModelReader(pomFiles, dir, bmkegParentVersion);
		
		List<File> viewFiles = reader.getViewFiles();
		List<File> dataFiles = reader.getDataFiles();
		UMLmodel model = reader.getModel();
		
		String group = reader.getGroup();
		String artifactId = reader.getArtifactId();
		String version = reader.getVersion();
		
		// ~~~~~~~~~~~~~~~~~~~~
		// Owl component build
		// ~~~~~~~~~~~~~~~~~~~~
		
		OwlUmlBuilder owl = new OwlUmlBuilder();
		owl.setUmlModel(model);
		owl.convertAttributes();
		
		OwlAPIUtility owlUtil = new OwlAPIUtility();
			
		String owlFileName = artifactId + "-" + version + ".owl";
		File owlFile = new File(dir + "/" + owlFileName);
		
		if( owlFile.exists() ) 
			owlFile.delete();
		
		OWLOntology o = owlUtil.createOntology(model.getUrl(),
				owlFile.getAbsolutePath());
		
		owlUtil.setPrefix(model.getUrl());
		//owlUtil.addOntologyMetadata(o);
		
		Map<String, UMLclass> classMap = model.listClasses(".*");
	
		Set<String> toOmit = new HashSet<String>();
	
		//
		// Add prefixes for each subdirectory containing classes
		//
		Map<String, String> prefixes = new HashMap<String, String>();
		for( String addr: classMap.keySet() ) {
			
			UMLclass c = classMap.get(addr);
	
			String prefix = c.getPkg().readPrefix();
			
			if(prefix.length() == 0) 
				continue;
			
			String url = c.getPkg().readUrl() + "#";
			
			prefixes.put(prefix, url);
			
		}
		
		for( String prefixName : prefixes.keySet() ) {
			owlUtil.setPrefix(prefixName + ":", prefixes.get(prefixName));
		}
		
		//
		// Add each class and name it.
		//
		Iterator<String> cIt = classMap.keySet().iterator();
		while (cIt.hasNext()) {
			String addr = cIt.next();
			UMLclass c = classMap.get(addr);
	
			addr = addr.substring(2, addr.length());
	
			// Check to see if the class is a set backing table...
			// if so don't generate the source code.
			if ( c.isDataType() || 
					(c.getStereotype() != null && c.getStereotype().equals("Link"))
					|| toOmit.contains(c.getBaseName())) {
				continue;
			}

			String url = c.readUrl();
			owlUtil.addClass(c.readPrefix() + ":" + c.getBaseName(), o);
			
			owlUtil.addNameComment(url, c.getBaseName(), o);
	
			String docs = c.getDocumentation();
			
			if (docs != null && docs.length() > 0) {
				owlUtil.addExternalAnnotation(url, "definition", docs, o);
			}
			
		}
	
		//
		// Add inheritance relationships.
		//
		cIt = classMap.keySet().iterator();
		while (cIt.hasNext()) {
			String addr = cIt.next();
			UMLclass c = classMap.get(addr);
		
			// Check to see if the class is a set backing table...
			// if so don't generate the source code.
			if ((c.getStereotype() != null && c.getStereotype().equals("Link"))
					|| toOmit.contains(c.getBaseName())) {
				continue;
			}
	
			UMLclass parent = c.getParent();
	
			if (parent != null) {
	
				if (toOmit.contains(parent.getBaseName())) {
					continue;
				}
	
				owlUtil.addSubClassToClass(parent.readPrefix() + ":" + parent.getBaseName(), 
						c.readPrefix() + ":" + c.getBaseName(), o);
	
			}
	
		}
	
		//
		// Add datatype & object type properties to classes from UML attributes
		// and roles.
		// - note that OWL uses universal definitions for properties.
		// They are not scoped to the enclosing class, so we will check the UML
		// definitions and
		// throw an exception if two properties have the same name and a
		// different class as it's
		// range.
		//
		cIt = classMap.keySet().iterator();
		while (cIt.hasNext()) {
			String addr = cIt.next();
			UMLclass c = classMap.get(addr);
	
			// Check to see if the class is a set backing table...
			// if so don't generate the source code.
			if ((c.getStereotype() != null && c.getStereotype().equals("Link"))
					|| toOmit.contains(c.getBaseName())) {
				continue;
			}
	
			owlUtil.constructAllRestrictionsForUMLClass(c, o);
	
		}
	
		owlUtil.constructAllDomainRestrictions(o);
	
		owlUtil.saveOntology(o);
	
		logger.info("Ontology built at:" + owlFile.getPath() );

	}
	
	
}
