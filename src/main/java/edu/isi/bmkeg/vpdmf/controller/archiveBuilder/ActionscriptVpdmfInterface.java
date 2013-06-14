package edu.isi.bmkeg.vpdmf.controller.archiveBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import com.google.common.io.Files;

import edu.isi.bmkeg.uml.interfaces.JavaUmlInterface;
import edu.isi.bmkeg.uml.model.UMLclass;
import edu.isi.bmkeg.uml.model.UMLpackage;
import edu.isi.bmkeg.utils.Converters;
import edu.isi.bmkeg.utils.TextUtils;
import edu.isi.bmkeg.utils.mvnRunner.LocalMavenInstall;
import edu.isi.bmkeg.vpdmf.model.definitions.VPDMf;
import edu.isi.bmkeg.vpdmf.model.definitions.ViewDefinition;

public class ActionscriptVpdmfInterface {

	Logger log = Logger.getLogger("ActionscriptVpdmfInterface");

	private VPDMf top;
	
	private UMLpackage pp;
	private File srcDir;
	private Map<String, File> filesInSrc;
	
	public ActionscriptVpdmfInterface(VPDMf top) throws Exception {
		this.top = top;
	}
	
	public void buildServiceMavenProject(File srcZipFile, File swcFile, 
			String group, String artifactId, String version,
			String repoId, String repoUrl) throws Exception {
				
		if( group == null || group.length() == 0 ) {
			group = "bmkeg.isi.edu";
		}
		
		File targetDir = srcZipFile.getParentFile();

		String commandsString = "";

		File tempUnzippedDirectory = Files.createTempDir();
		
		tempUnzippedDirectory.deleteOnExit();
		String dAddr = tempUnzippedDirectory.getAbsolutePath();

		String asTemplateZipPath = ClassLoader.getSystemResource("edu/isi/bmkeg/vpdmf/as/as-template.zip").getFile();
		File asTemplateZip = new File(asTemplateZipPath);
		
		Converters.unzipIt(asTemplateZip, tempUnzippedDirectory);

		// 1. rename the base directory
		File unZippedSwf = new File(dAddr + "/as-template");
		File swfProject = new File(dAddr + "/" + artifactId + "-as-services");
		unZippedSwf.renameTo(swfProject);		

		// 2. edit the pom
		File pomFile = new File(swfProject.getPath() + "/pom.xml");
		String pomCode = TextUtils.readFileToString(pomFile);
		pomCode = pomCode.replaceAll("\\[GROUPID\\]", group);
		pomCode = pomCode.replaceAll("\\[ARTIFACTID\\]", artifactId + "-as-services");
		pomCode = pomCode.replaceAll("\\[VERSION\\]", version);
		
		// add some stuff for us.
		String lu = "<dependencies>";
		String toInsert = "		<dependency>\n" +
				"			<groupId>" + group + "</groupId>\n" +
				"			<artifactId>" + artifactId + "-as" + "</artifactId>\n" +
				"			<version>" + version + "</version>\n" +
				"			<type>swc</type>\n" +
				"		</dependency>\n";
		int insertLoc = pomCode.lastIndexOf(lu);
		pomCode = pomCode.substring(0,insertLoc+lu.length()+1) 
				+ toInsert + 
				pomCode.substring(insertLoc+lu.length()+2, pomCode.length());
		
		String distribMgmt = "";
		if( version.endsWith("-SNAPSHOT") ) {
			distribMgmt += "		<snapshotRepository>\n";
			distribMgmt += "			<id>" + repoId + "</id>\n";
			distribMgmt += "			<name>SNAPSHOT repository</name>\n";
			distribMgmt += "			<url>" + repoUrl + "</url>\n";
			distribMgmt += "		</snapshotRepository>\n";
		} else {
			distribMgmt += "		<repository>\n";
			distribMgmt += "			<id>" + repoId + "</id>\n";
			distribMgmt += "			<name>Repository</name>\n";
			distribMgmt += "			<url>" + repoUrl + "</url>\n";
			distribMgmt += "		</repository>\n";
		}
		pomCode = pomCode.replaceAll("\\[DISTRIBUTION_MANAGMENT\\]", distribMgmt);
		
		FileUtils.writeStringToFile(pomFile, pomCode);
		
		// 3. add the extra source code
		this.srcDir = new File(swfProject + "/src/main/flex/");
		
		this.filesInSrc = new HashMap<String, File>();
		
		for( UMLpackage p: this.top.getUmlModel().listPackages().values() ) {
			if( !p.getBaseName().equals("model") || 
					p.getParent().getBaseName().equals("vpdmf")) {
				continue;
			}
			this.pp = p.getParent();
			
			String s = pp.getBaseName();
			s = s.substring(0,1).toUpperCase() + s.substring(1, s.length());
			
			File f = this.prepareCode(".rl.services", "I" + s + "Service");
			String code = this.generateServiceInterfaceCode(pp);
			FileUtils.writeStringToFile(f, code);
			
			f = this.prepareCode(".rl.services.impl", s + "ServiceImpl");
			code = this.generateServiceCode(pp);
			FileUtils.writeStringToFile(f, code);

			f = this.prepareCode(".rl.services.serverInteraction", "I" + s + "Server");
			code = this.generateServerCode(pp);
			FileUtils.writeStringToFile(f, code);
			
			f = this.prepareCode(".rl.services.serverInteraction.impl", s + "ServerImpl");
			code = this.generateServerImplCode(pp);
			FileUtils.writeStringToFile(f, code);
			
			Set<UMLclass> cList = new HashSet<UMLclass>();
			for( ViewDefinition vd: this.top.getViews().values()) {
				if( vd.getType() != ViewDefinition.DATA &&
						vd.getType() != ViewDefinition.COLLECTION && 
						vd.getType() != ViewDefinition.LOOKUP ) 
					continue;
				
				UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
				if( !c.getPkg().readPackageAddress().contains( pp.readPackageAddress() )) {
					continue; 
				}
				
				if( vd.getType() != ViewDefinition.LOOKUP ) {
				
					f = this.prepareCode(".rl.events", "Delete" + vd.getName() + "Event");
					code = this.generateDeleteEventCode(pp, vd);
					FileUtils.writeStringToFile(f, code);
	
					f = this.prepareCode(".rl.events", "Delete" + vd.getName() + "ResultEvent");
					code = this.generateDeleteResultEventCode(pp, vd);
					FileUtils.writeStringToFile(f, code);
	
					f = this.prepareCode(".rl.events", "Insert" + vd.getName() + "Event");
					code = this.generateInsertEventCode(pp, vd);
					FileUtils.writeStringToFile(f, code);
	
					f = this.prepareCode(".rl.events", "Insert" + vd.getName() + "ResultEvent");
					code = this.generateInsertResultEventCode(pp, vd);
					FileUtils.writeStringToFile(f, code);
					
					f = this.prepareCode(".rl.events", "Update" + vd.getName() + "Event");
					code = this.generateUpdateEventCode(pp, vd);
					FileUtils.writeStringToFile(f, code);
					
					f = this.prepareCode(".rl.events", "Update" + vd.getName() + "ResultEvent");
					code = this.generateUpdateResultEventCode(pp, vd);
					FileUtils.writeStringToFile(f, code);
				
				}
				
				f = this.prepareCode(".rl.events", "Count" + vd.getName() + "Event");
				code = this.generateCountEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "Count" + vd.getName() + "ResultEvent");
				code = this.generateCountResultEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "Find" + vd.getName() + "ByIdEvent");
				code = this.generateFindByIdEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "Find" + vd.getName() + "ByIdResultEvent");
				code = this.generateFindByIdResultEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);
				
				f = this.prepareCode(".rl.events", "List" + vd.getName() + "Event");
				code = this.generateListEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "List" + vd.getName() + "ResultEvent");
				code = this.generateListResultEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "List" + vd.getName() + "PagedEvent");
				code = this.generatePagedListEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "List" + vd.getName() + "PagedResultEvent");
				code = this.generatePagedListResultEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "Retrieve" + vd.getName() + "Event");
				code = this.generateRetrieveEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "Retrieve" + vd.getName() + "ResultEvent");
				code = this.generateRetrieveResultEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "Retrieve" + vd.getName() + "PagedEvent");
				code = this.generatePagedRetrieveEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "Retrieve" + vd.getName() + "PagedResultEvent");
				code = this.generatePagedRetrieveResultEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);
				
			}
			
			Set<UMLclass> vList = new HashSet<UMLclass>();
			for( ViewDefinition vd: this.top.getViews().values()) {
				if( vd.getType() != ViewDefinition.LOOKUP ) 
					continue;
				
				UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
				if( !c.getPkg().readPackageAddress().contains( pp.readPackageAddress() )) {
					continue; 
				}
				
				f = this.prepareCode(".rl.events", "List" + vd.getName() + "Event");
				code = this.generateListEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "List" + vd.getName() + "ResultEvent");
				code = this.generateListResultEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "List" + vd.getName() + "PagedEvent");
				code = this.generatePagedListEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);

				f = this.prepareCode(".rl.events", "List" + vd.getName() + "PagedResultEvent");
				code = this.generatePagedListResultEventCode(pp, vd);
				FileUtils.writeStringToFile(f, code);
				
			}
			
		}
		
		Converters.zipPrep(swfProject.getPath(), swfProject, filesInSrc);
		Converters.zipIt(filesInSrc, srcZipFile);
		
		if(swcFile == null)
			return;

		String out = LocalMavenInstall.runMavenCommand("clean compile -f " + pomFile.getAbsolutePath());
		log.debug(out);
        File f1 = new File(swfProject.getPath() + "/target/" + artifactId + "-as-services-"+ version + ".swc" );

        if( !f1.exists() )
        	throw new Exception("Build for " + f1.getName() + " failed. Check source\n ERROR:" + out);
        
        InputStream from = new FileInputStream(f1);
        OutputStream to = new FileOutputStream(swcFile);

        byte[] buff = new byte[1024];
        int len;
        while ((len = from.read(buff)) > 0) {
        	to.write(buff, 0, len);
        }
        from.close();
        to.close();

		Converters.recursivelyDeleteFiles(tempUnzippedDirectory);

	}
	
	private File prepareCode(String pkgSuffix, String cName) {
		String addr = pp.readPackageAddress() + pkgSuffix;
		addr = addr.replaceAll("\\.", "/"); 
		File dir = new File( srcDir.getPath() + "/" + addr );
		dir.mkdirs();
		return new File(dir.getPath() + "/" + cName + ".as"); 		
	}
	
	
	public String generateServiceInterfaceCode(UMLpackage pkg) throws Exception {
		
		String code = "";
		
		String addr = pkg.readPackageAddress();
		
		//
		// search through this package and find all the data views 
		// that have primary class in this package
		//
		Set<UMLclass> importsSet = new HashSet<UMLclass>();
		Map<ViewDefinition, UMLclass> cList = new HashMap<ViewDefinition, UMLclass>();
		for( ViewDefinition vd: this.top.getViews().values()) {
			if( vd.getType() != ViewDefinition.DATA && 
					vd.getType() != ViewDefinition.COLLECTION && 
					vd.getType() != ViewDefinition.LOOKUP) 
				continue;
			UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
			if( c.getPkg().readPackageAddress().contains( addr )) {
				cList.put(vd, c);
			}
			importsSet.addAll(vd.getPrimaryPrimitive().getClasses());
		}
		
		code += "package " + pkg.readPackageAddress() + ".rl.services\n{\n\n";
		
		for( UMLclass impStr: importsSet) {
			
			String cAddr = impStr.readClassAddress();
			code += "import " + cAddr + ";\n";
			
			cAddr = cAddr.replaceAll("\\.model\\.", ".model.qo.");
			cAddr += "_qo";
			code += "import " + cAddr + ";\n";

		}
		
		String s = pkg.getBaseName();
		s = s.substring(0,1).toUpperCase() + s.substring(1, s.length());
		code += "\n	public interface I" + s + "Service {\n\n";

		code += "		// ~~~~~~~~~~~~~~~\n";
		code += "		// Count functions\n";
		code += "		// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "		function count" + key.getName() 
					+ "(object:" + c.getBaseName() + "_qo):void;\n\n";			
		}
		
		code += "		// ~~~~~~~~~~~~~~~~\n";
		code += "		// Insert functions\n";
		code += "		// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "		function insert" + key.getName() + "(" + s + ":"   
					+ c.getBaseName() + "):void;\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~\n";
		code += "		// Update functions\n";
		code += "		// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "		function update" + key.getName() + "(" + s + ":"   
					+ c.getBaseName() + "):void;\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~\n";
		code += "		// Delete functions\n";
		code += "		// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			code += "		function delete" + key.getName()
					+ "(id:Number):void;\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~~~\n";
		code += "		// FindById functions\n";
		code += "		// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		function find" + key.getName()
					+ "ById(id:Number):void;\n\n";			
		}		

		code += "		// ~~~~~~~~~~~~~~~~~~\n";
		code += "		// Retrieve functions\n";
		code += "		// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "		function retrieve" + key.getName() 
					+ "Paged(object:" + c.getBaseName() + ", offset:int, cnt:int):void;\n\n";			
			code += "		function retrieve" + key.getName() 
					+ "(object:" + c.getBaseName() + "):void;\n\n";			
		}		
		

		code += "		// ~~~~~~~~~~~~~~~\n";
		code += "		// List functions\n";
		code += "		// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "		function list" + key.getName() 
					+ "Paged(object:" + c.getBaseName() + "_qo, offset:int, cnt:int):void;\n\n";			
			code += "		function list" + key.getName()
					+ "(object:" + c.getBaseName() + "_qo):void;\n\n";			
		}			
		
		return code + "	}\n\n}\n";
		
	}

	public String generateServiceCode(UMLpackage pkg) throws Exception {
		
		String code = "";
		
		String addr = pkg.readPackageAddress();
		
		//
		// search through this package and find all the data views 
		// that have primary class in this package
		//
		Set<UMLclass> importsSet = new HashSet<UMLclass>();
		Map<ViewDefinition, UMLclass> cList = new HashMap<ViewDefinition, UMLclass>();
		for( ViewDefinition vd: this.top.getViews().values()) {
			if( vd.getType() != ViewDefinition.DATA && 
					vd.getType() != ViewDefinition.COLLECTION && 
					vd.getType() != ViewDefinition.LOOKUP) 
				continue;
			UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
			if( c.getPkg().readPackageAddress().contains( addr )) {
				cList.put(vd, c);
			}
			importsSet.addAll(vd.getPrimaryPrimitive().getClasses());
		}
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.services.impl\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import mx.collections.ArrayCollection;\n";
		code += "	import mx.collections.IList;\n";
		code += "	import mx.rpc.AbstractService;\n";
		code += "	import mx.rpc.AsyncResponder;\n";
		code += "	import mx.rpc.AsyncToken;\n";
		code += "	import mx.rpc.events.FaultEvent;\n";
		code += "	import mx.rpc.events.ResultEvent;\n";
		code += "	import org.robotlegs.mvcs.Actor;\n\n";
		
		for( UMLclass impStr: importsSet) {
			
			String cAddr = impStr.readClassAddress();
			code += "import " + cAddr + ";\n";
			
			cAddr = cAddr.replaceAll("\\.model\\.", ".model.qo.");
			cAddr += "_qo";
			code += "import " + cAddr + ";\n";

		}

		code += "	import " + pkg.readPackageAddress() + ".rl.events.*;\n\n";
		code += "	import " + pkg.readPackageAddress() + ".rl.services.serverInteraction.*;\n\n";
		code += "	import " + pkg.readPackageAddress() + ".rl.services.*;\n\n";		

		code += "	import edu.isi.bmkeg.utils.dao.*;\n\n";		

		
		code += "	public class " + pTitle + "ServiceImpl extends Actor implements I" 
				+ pTitle + "Service {\n\n";

		code += "		private var _server:I" + pTitle + "Server;\n\n";

		code += "		[Inject]\n";
		code += "		public function get server():I" + pTitle + "Server\n\n";
		code += "		{\n";
		code += "			return _server;\n";
		code += "		}\n\n";
		
		code += "		public function set server(s:I" + pTitle + "Server):void\n";
		code += "		{\n";
		code += "			_server = s;\n";
		code += "			initServer();\n";
		code += "		}\n\n";

		code += "		private function initServer():void\n";
		code += "		{\n";
		code += "			if (_server is AbstractService)\n";
		code += "			{\n";
		code += "				AbstractService(_server)" 
				+ ".addEventListener(FaultEvent.FAULT,faultHandler);\n";
		code += "			}\n";
		code += "		}\n\n";
		
		code += "		private function asyncResponderFailHandler(fail:Object, token:Object):void\n";
		code += "		{\n";
		code += "			faultHandler(fail as FaultEvent);\n";
		code += "		}\n\n";
		
		code += "		private function faultHandler(event:FaultEvent):void\n";
		code += "		{\n";
		code += "			var failureEvent:ServiceFailureEvent = new ServiceFailureEvent(event);\n";
		code += "			dispatch(failureEvent);\n";
		code += "		}\n\n";		
		
		code += "		// ~~~~~~~~~~~~~~~\n";
		code += "		// Count functions\n";
		code += "		// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {			
			UMLclass c = cList.get(key);
			String s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "		public function count" + key.getName() 
					+ "(object:" + c.getBaseName() + "_qo):void {\n";			
			code += "			server.count" + key.getName() + ".cancel();\n";
			code += "			server.count" + key.getName() + ".addEventListener(ResultEvent.RESULT, count" 
					+ key.getName() + "ResultHandler);\n";
			code += "			server.count" + key.getName() + ".send(object);\n";
			code += "		}\n\n";
		}
		
		code += "		// ~~~~~~~~~~~~~~~~\n";
		code += "		// Insert functions\n";
		code += "		// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;			
			UMLclass c = cList.get(key);
			code += "		public function insert" + key.getName() 
					+ "(object:" + c.getBaseName() + "):void {\n";
			code += "			server.insert" + key.getName() + ".cancel();\n";
			code += "			server.insert" + key.getName() 
					+ ".addEventListener(ResultEvent.RESULT, insert" 
					+ key.getName() + "ResultHandler);\n";
			code += "			server.insert" + key.getName() + ".send(object);\n";
			code += "		}\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~\n";
		code += "		// Update functions\n";
		code += "		// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			code += "		public function update" + key.getName() 
					+ "(object:" + c.getBaseName() + "):void {\n";
			code += "			server.update" + key.getName() + ".cancel();\n";
			code += "			server.update" + key.getName() 
					+ ".addEventListener(ResultEvent.RESULT, update" 
					+ key.getName() + "ResultHandler);\n";
			code += "			server.update" + key.getName() + ".send(object);\n";
			code += "		}\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~\n";
		code += "		// Delete functions\n";
		code += "		// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			code += "		public function delete" + key.getName() 
					+ "(id:Number):void {\n";
			code += "			server.delete" + key.getName() + ".cancel();\n";
			code += "			server.delete" + key.getName() 
					+ ".addEventListener(ResultEvent.RESULT, delete" 
					+ key.getName() + "ResultHandler);\n";
			code += "			server.delete" + key.getName() + ".send(id);\n";
			code += "		}\n\n";		
		}

		code += "		// ~~~~~~~~~~~~~~~~~~\n";
		code += "		// FindById functions\n";
		code += "		// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		public function find" + key.getName() 
					+ "ById(id:Number):void {\n";
			code += "			server.find" + key.getName() + "ById.cancel();\n";
			code += "			server.find" + key.getName() 
					+ "ById.addEventListener(ResultEvent.RESULT, find" 
					+ key.getName() + "ByIdResultHandler);\n";
			code += "			server.find" + key.getName() + "ById.send(id);\n";
			code += "		}\n\n";		
		}		

		code += "		// ~~~~~~~~~~~~~~~~~~\n";
		code += "		// Retrieve functions\n";
		code += "		// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "		public function retrieve" + key.getName() 
					+ "(object:" + c.getBaseName() + "):void {\n";
			code += "			server.retrieve" + key.getName() + ".cancel();\n";
			code += "			server.retrieve" + key.getName() 
					+ ".addEventListener(ResultEvent.RESULT, retrieve" 
					+ key.getName() + "ResultHandler);\n";
			code += "			server.retrieve" + key.getName() + ".send(object);\n";
			code += "		}\n\n";
			code += "		public function retrieve" + key.getName() 
					+ "Paged(object:" + c.getBaseName() + ", offset:int, cnt:int):void {\n";
			code += "			server.retrieve" + key.getName() + "Paged.cancel();\n";
			code += "			server.retrieve" + key.getName() 
					+ "Paged.addEventListener(ResultEvent.RESULT, retrieve" 
					+ key.getName() + "PagedResultHandler);\n";
			code += "			server.retrieve" + key.getName() + "Paged.send(object, offset, cnt);\n";
			code += "		}\n\n";
		}		
		

		code += "		// ~~~~~~~~~~~~~~~\n";
		code += "		// List functions\n";
		code += "		// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "		public function list" + key.getName() 
					+ "(object:" + c.getBaseName() + "_qo):void {\n";
			code += "			server.list" + key.getName() + ".cancel();\n";
			code += "			server.list" + key.getName() 
					+ ".addEventListener(ResultEvent.RESULT, list" 
					+ key.getName() + "ResultHandler);\n";
			code += "			server.list" + key.getName() + ".send(object);\n";
			code += "		}\n\n";
			code += "		public function list" + key.getName() 
					+ "Paged(object:" + c.getBaseName() + "_qo, offset:int, cnt:int):void {\n";
			code += "			server.list" + key.getName() + "Paged.cancel();\n";
			
			/*ADDING AsyncTokens and AsyncResponders... here's the old code
		    code += "			server.list" + key.getName() 
					+ "Paged.addEventListener(ResultEvent.RESULT, list" 
					+ key.getName() + "PagedResultHandler);\n";
			code += "			server.list" + key.getName() + "Paged.send(object, offset, cnt);\n";
			NEW CODE BELOW*/
			
			code += "			var token:AsyncToken = server.list" + key.getName() 
					+ "Paged.send(object, offset, cnt);\n"; 
			code += "			var synRes:AsyncResponder = new AsyncResponder(\n";
			code += "						list" + key.getName() + "PagedResultHandler,\n";
			code += "						asyncResponderFailHandler,\n";
			code += "						{offset:offset});\n";
			code += "			token.addResponder(synRes);\n";
			code += "		}\n\n";
		}		
		
		code += "		// ~~~~~~~~~~~~~~~~~~~~\n";
		code += "		// Count result handler\n";
		code += "		// ~~~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		private function count" + key.getName() 
					+ "ResultHandler(event:ResultEvent):void\n";
			code += "		{\n";			
			code += "			var cnt:int = int(event.result);\n";
			code += "			dispatch(new Count" + key.getName() + "ResultEvent(cnt));\n";
			code += "		}\n\n";
		}
		
		code += "		// ~~~~~~~~~~~~~~~~~~~~~\n";
		code += "		// Insert result handler\n";
		code += "		// ~~~~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			code += "		private function insert" + key.getName() 
					+ "ResultHandler(event:ResultEvent):void\n";
			code += "		{\n";			
			code += "			var id:Number = Number(event.result);\n";
			code += "			dispatch(new Insert" + key.getName() + "ResultEvent(id));\n";
			code += "		}\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~~~~~~\n";
		code += "		// Update result handler\n";
		code += "		// ~~~~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			code += "		private function update" + key.getName() 
					+ "ResultHandler(event:ResultEvent):void\n";
			code += "		{\n";			
			code += "			var id:Number = Number(event.result);\n";
			code += "			dispatch(new Update" + key.getName() + "ResultEvent(id));\n";
			code += "	}\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~~~~~~\n";
		code += "		// Delete result handler\n";
		code += "		// ~~~~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			code += "		private function delete" + key.getName() 
					+ "ResultHandler(event:ResultEvent):void\n";
			code += "		{\n";			
			code += "			dispatch(new Delete" + key.getName() + "ResultEvent());\n";
			code += "		}\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~~~~~~~~\n";
		code += "		// FindById result handler\n";
		code += "		// ~~~~~~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "		private function find" + key.getName() 
					+ "ByIdResultHandler(event:ResultEvent):void\n";
			code += "		{\n";			
			code += "			var object:" + c.getBaseName() 
					+ " = " + c.getBaseName() + "(event.result);\n";
			code += "			dispatch(new Find" + key.getName() + "ByIdResultEvent(object));\n";
			code += "		}\n\n";
		}		

		code += "		// ~~~~~~~~~~~~~~~~~~~~~~~\n";
		code += "		// Retrieve result handler\n";
		code += "		// ~~~~~~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		private function retrieve" + key.getName() 
					+ "ResultHandler(event:ResultEvent):void\n";
			code += "		{\n";			
			code += "			var list:ArrayCollection = ArrayCollection(event.result);\n";
			code += "			dispatch(new Retrieve" + key.getName() + "ResultEvent(list));\n";
			code += "		}\n\n";
			code += "		private function retrieve" + key.getName() 
					+ "PagedResultHandler(event:ResultEvent):void\n";
			code += "		{\n";			
			code += "			var list:ArrayCollection = ArrayCollection(event.result);\n";
			code += "			dispatch(new Retrieve" + key.getName() + "PagedResultEvent(list));\n";
			code += "		}\n\n";		}		
		

		code += "		// ~~~~~~~~~~~~~~~~~~~\n";
		code += "		// List result handler\n";
		code += "		// ~~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		private function list" + key.getName() 
					+ "ResultHandler(event:ResultEvent):void\n";
			code += "		{\n";			
			code += "			var list:ArrayCollection = ArrayCollection(event.result);\n";
			code += "			dispatch(new List" + key.getName() + "ResultEvent(list));\n";
			code += "		}\n\n";
			code += "		private function list" + key.getName() 
					+ "PagedResultHandler(event:ResultEvent, token:Object):void\n";
			code += "		{\n";			
			code += "			var list:ArrayCollection = event.result as ArrayCollection;\n";
			code += "			var offset:int = int(token.offset);\n";
			code += "			dispatch(new List" + key.getName() + "PagedResultEvent(list, offset));\n";
			code += "		}\n\n";		
					
		}	
		
		return code + "	}\n\n}\n";
		
	}

	public String generateDeleteEventCode(UMLpackage pkg, ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Delete" + vd.getName() + "Event extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const DELETE_" + vd.getName().toUpperCase() + ":String = \"delete"
				+ vd.getName() + "\";\n\n"; 

		code += "		public var object:" + c.getBaseName() + ";\n\n"; 

		code += "		public function Delete" + vd.getName() + "Event(object:" + c.getBaseName() + ")\n";
		code += "		{\n";
		code += "			super(DELETE_" + vd.getName().toUpperCase() + ");\n";
		code += "			this.object = object;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Delete" + vd.getName() + "Event(object);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateDeleteResultEventCode(UMLpackage pkg, 
			ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
			
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Delete" + vd.getName() + "ResultEvent extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const DELETE_" + vd.getName().toUpperCase() 
				+ ":String = \"delete"
				+ vd.getName() + "\";\n\n"; 

		code += "		public function Delete" + vd.getName() + "ResultEvent()\n";
		code += "		{\n";
		code += "			super(DELETE_" + vd.getName().toUpperCase() + ");\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Delete" + vd.getName() + "ResultEvent();\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateFindByIdEventCode(UMLpackage pkg, ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Find" + vd.getName() + "ByIdEvent extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const FIND_" + vd.getName().toUpperCase() 
				+ "_BY_ID:String = \"find"
				+ vd.getName() + "ById\";\n\n"; 

		code += "		public var id:Number;\n\n"; 

		code += "		public function Find" + vd.getName() + "ByIdEvent(id:Number)\n";
		code += "		{\n";
		code += "			super(FIND_" + vd.getName().toUpperCase() + "_BY_ID);\n";
		code += "			this.id = id;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Find" + vd.getName() + "ByIdEvent(id);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateFindByIdResultEventCode(UMLpackage pkg, 
			ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
			
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Find" + vd.getName() + "ByIdResultEvent extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const FIND_" + vd.getName().toUpperCase() 
				+ "BY_ID_RESULT:String = \"find"
				+ vd.getName() + "ByIdResult\";\n\n"; 

		code += "		public var object:" + c.getBaseName() + ";\n\n"; 

		code += "		public function Find" + vd.getName() 
				+ "ByIdResultEvent(object:" + c.getBaseName() + ")\n";
		code += "		{\n";
		code += "			super(FIND_" + vd.getName().toUpperCase() 
				+ "BY_ID_RESULT);\n";
		code += "			this.object = object;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Find" + vd.getName() + "ByIdResultEvent(object);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateCountEventCode(UMLpackage pkg, ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		// switch to qo classes for list services and events
		String addr = c.readClassAddress();
		addr = addr.replaceAll("\\.model\\.", ".model.qo.");
		code += "	import " + addr + "_qo;\n\n";
		
		code += "	public class Count" + vd.getName() + "Event extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const COUNT_" + vd.getName().toUpperCase() + ":String = \"count"
				+ vd.getName() + "\";\n\n"; 

		code += "		public var object:" + c.getBaseName() + "_qo;\n\n"; 

		code += "		public function Count" + vd.getName() + "Event(object:" 
				+ c.getBaseName() + "_qo)\n";
		code += "		{\n";
		code += "			super(COUNT_" + vd.getName().toUpperCase() + ");\n";
		code += "			this.object = object;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Count" + vd.getName() + "Event(object);\n";
		code += "		}\n\n";
				
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateCountResultEventCode(UMLpackage pkg, ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Count" + vd.getName() + "ResultEvent extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const COUNT_" + vd.getName().toUpperCase() 
				+ "_RESULT:String = \"count"
				+ vd.getName() + "Result\";\n\n"; 

		code += "		public var count:Number;\n\n"; 

		code += "		public function Count" + vd.getName() + "ResultEvent(count:Number)\n";
		code += "		{\n";
		code += "			super(COUNT_" + vd.getName().toUpperCase() + "_RESULT);\n";
		code += "			this.count = count;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Count" + vd.getName() + "ResultEvent(count);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateInsertEventCode(UMLpackage pkg, ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Insert" + vd.getName() + "Event extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const INSERT_" + vd.getName().toUpperCase() + ":String = \"insert"
				+ vd.getName() + "\";\n\n"; 

		code += "		public var object:" + c.getBaseName() + ";\n\n"; 

		code += "		public function Insert" + vd.getName() + "Event(object:" 
				+ c.getBaseName() + ")\n";
		code += "		{\n";
		code += "			super(INSERT_" + vd.getName().toUpperCase() + ");\n";
		code += "			this.object = object;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Insert" + vd.getName() + "Event(object);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateInsertResultEventCode(UMLpackage pkg, ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Insert" + vd.getName() + "ResultEvent extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const INSERT_" + vd.getName().toUpperCase() 
				+ "_RESULT:String = \"count"
				+ vd.getName() + "Result\";\n\n"; 

		code += "		public var id:Number;\n\n"; 

		code += "		public function Insert" + vd.getName() + "ResultEvent(id:Number)\n";
		code += "		{\n";
		code += "			super(INSERT_" + vd.getName().toUpperCase() + "_RESULT);\n";
		code += "			this.id = id;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Insert" + vd.getName() + "ResultEvent(id);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateUpdateEventCode(UMLpackage pkg, ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Update" + vd.getName() + "Event extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const UPDATE_" + vd.getName().toUpperCase() + ":String = \"update"
				+ vd.getName() + "\";\n\n"; 

		code += "		public var object:" + c.getBaseName() + ";\n\n"; 

		code += "		public function Update" + vd.getName() + "Event(object:" 
				+ c.getBaseName() + ")\n";
		code += "		{\n";
		code += "			super(UPDATE_" + vd.getName().toUpperCase() + ");\n";
		code += "			this.object = object;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Update" + vd.getName() + "Event(object);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateUpdateResultEventCode(UMLpackage pkg, ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Update" + vd.getName() + "ResultEvent extends Event\n";
		code += "		{\n\n";
		
		code += "		public static const UPDATE_" + vd.getName().toUpperCase() 
				+ "_RESULT:String = \"update"
				+ vd.getName() + "Result\";\n\n"; 

		code += "		public var id:Number;\n\n"; 
		
		code += "		public function Update" + vd.getName() + "ResultEvent(id:int)\n";
		code += "		{\n";
		code += "			super(UPDATE_" + vd.getName().toUpperCase() + "_RESULT);\n";
		code += "			this.id = id;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Update" + vd.getName() + "ResultEvent(id);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateRetrieveEventCode(UMLpackage pkg, ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Retrieve" + vd.getName() + "Event extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const RETRIEVE_" + vd.getName().toUpperCase() + ":String = \"retrieve"
				+ vd.getName() + "\";\n\n"; 

		code += "		public var object:" + c.getBaseName() + ";\n\n"; 

		code += "		public function Retrieve" + vd.getName() + "Event(object:" 
				+ c.getBaseName() + ")\n";
		code += "		{\n";
		code += "			super(RETRIEVE_" + vd.getName().toUpperCase() + ");\n";
		code += "			this.object = object;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Retrieve" + vd.getName() + "Event(object);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateRetrieveResultEventCode(UMLpackage pkg, 
			ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
			
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import mx.collections.ArrayCollection;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Retrieve" + vd.getName() + "ResultEvent extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const RETRIEVE_" + vd.getName().toUpperCase() 
				+ "_RESULT:String = \"retrieve"
				+ vd.getName() + "Result\";\n\n"; 

		code += "		public var list:ArrayCollection;\n\n"; 

		code += "		public function Retrieve" + vd.getName() 
				+ "ResultEvent(list:ArrayCollection)\n";
		code += "		{\n";
		code += "			super(RETRIEVE_" + vd.getName().toUpperCase() 
				+ "_RESULT);\n";
		code += "			this.list = list;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Retrieve" + vd.getName() + "ResultEvent(list);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generatePagedRetrieveEventCode(UMLpackage pkg, ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Retrieve" + vd.getName() + "PagedEvent extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const RETRIEVE_" + vd.getName().toUpperCase() + "_PAGED:String = \"retrieve"
				+ vd.getName() + "Paged\";\n\n"; 

		code += "		public var object:" + c.getBaseName() + ";\n\n"; 
		code += "		public var offset:int;\n\n"; 
		code += "		public var cnt:int;\n\n"; 

		code += "		public function Retrieve" + vd.getName() + "PagedEvent(object:" 
				+ c.getBaseName() + ", offset:int, cnt:int)\n";
		code += "		{\n";
		code += "			super(RETRIEVE_" + vd.getName().toUpperCase() + "_PAGED);\n";
		code += "			this.object = object;\n";
		code += "			this.offset = offset;\n";
		code += "			this.cnt = cnt;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Retrieve" + vd.getName() + "PagedEvent(object, offset, cnt);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generatePagedRetrieveResultEventCode(UMLpackage pkg, 
			ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
			
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import mx.collections.ArrayCollection;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class Retrieve" + vd.getName() + "PagedResultEvent extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const RETRIEVE_" + vd.getName().toUpperCase() 
				+ "_PAGED_RESULT:String = \"retrieve"
				+ vd.getName() + "PagedResult\";\n\n"; 

		code += "		public var list:ArrayCollection;\n\n"; 

		code += "		public function Retrieve" + vd.getName() 
				+ "PagedResultEvent(list:ArrayCollection)\n";
		code += "		{\n";
		code += "			super(RETRIEVE_" + vd.getName().toUpperCase() 
				+ "_PAGED_RESULT);\n";
		code += "			this.list = list;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new Retrieve" + vd.getName() + "PagedResultEvent(list);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateListEventCode(UMLpackage pkg, ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		
		// switch to qo classes for list services and events
		String addr = c.readClassAddress();
		addr = addr.replaceAll("\\.model\\.", ".model.qo.");
		code += "	import " + addr + "_qo;\n\n";
		
		code += "	public class List" + vd.getName() + "Event extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const LIST_" + vd.getName().toUpperCase() 
				+ ":String = \"list" + vd.getName() + "\";\n\n"; 

		code += "		public var object:" + c.getBaseName() + "_qo;\n\n"; 

		code += "		public function List" + vd.getName() + "Event(object:" 
				+ c.getBaseName() + "_qo)\n";
		code += "		{\n";
		code += "			super(LIST_" + vd.getName().toUpperCase() + ");\n";
		code += "			this.object = object;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new List" + vd.getName() + "Event(object);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generateListResultEventCode(UMLpackage pkg, 
			ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
			
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import mx.collections.ArrayCollection;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class List" + vd.getName() + "ResultEvent extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const LIST_" + vd.getName().toUpperCase() 
				+ "_RESULT:String = \"list"
				+ vd.getName() + "Result\";\n\n"; 

		code += "		public var list:ArrayCollection;\n\n"; 

		code += "		public function List" + vd.getName() 
				+ "ResultEvent(list:ArrayCollection)\n";
		code += "		{\n";
		code += "			super(LIST_" + vd.getName().toUpperCase() 
				+ "_RESULT);\n";
		code += "			this.list = list;\n";
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new List" + vd.getName() + "ResultEvent(list);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generatePagedListEventCode(UMLpackage pkg, ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
		
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		
		// switch to qo classes for list services and events
		String addr = c.readClassAddress();
		addr = addr.replaceAll("\\.model\\.", ".model.qo.");
		code += "	import " + addr + "_qo;\n\n";
		
		code += "	public class List" + vd.getName() + "PagedEvent extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const LIST_" + vd.getName().toUpperCase() 
				+ "_PAGED:String = \"list" + vd.getName() + "\";\n\n"; 

		code += "		public var object:" + c.getBaseName() + "_qo;\n\n"; 
		code += "		public var offset:int;\n\n"; 
		code += "		public var cnt:int;\n\n"; 

		code += "		public function List" + vd.getName() + "PagedEvent(object:" 
				+ c.getBaseName() + "_qo, offset:int, cnt:int)\n";
		code += "		{\n";
		code += "			super(LIST_" + vd.getName().toUpperCase() + "_PAGED);\n";
		code += "			this.object = object;\n";
		code += "			this.offset = offset;\n";
		code += "			this.cnt = cnt;\n";
		
		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new List" + vd.getName() + "PagedEvent(object, offset, cnt);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	public String generatePagedListResultEventCode(UMLpackage pkg, 
			ViewDefinition vd) throws Exception {
		
		UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
		
		String code = "";
			
		String pTitle = pkg.getBaseName();
		pTitle = pTitle.substring(0,1).toUpperCase() + pTitle.substring(1, pTitle.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.events\n{\n\n";
		
		code += "	import flash.events.Event;\n";
		code += "	import mx.collections.ArrayCollection;\n";
		code += "	import " + c.readClassAddress() + ";\n\n";
		
		code += "	public class List" + vd.getName() + "PagedResultEvent extends Event\n";
		code += "		{\n\n";
	
		code += "		public static const LIST_" + vd.getName().toUpperCase() 
				+ "_PAGED_RESULT:String = \"list"
				+ vd.getName() + "PagedResult\";\n\n"; 

		code += "		public var list:ArrayCollection;\n\n"; 
		code += "		public var offset:int;\n\n"; 

		code += "		public function List" + vd.getName() 
				+ "PagedResultEvent(list:ArrayCollection, offset:int)\n";
		code += "		{\n";
		code += "			super(LIST_" + vd.getName().toUpperCase() 
				+ "_PAGED_RESULT);\n";
		code += "			this.list = list;\n";
		code += "			this.offset = offset;\n";

		code += "		}\n\n";
		
		code += "		override public function clone() : Event\n";
		code += "		{\n";
		code += "			return new List" + vd.getName() + "PagedResultEvent(list, offset);\n";
		code += "		}\n\n";
		
		code += "	}\n";
		code += "}\n";
		
		return code ;
		
	}
	
	
	public String generateServerCode(UMLpackage pkg) throws Exception {
		
		String code = "";
		
		String addr = pkg.readPackageAddress();
		
		//
		// search through this package and find all the data views 
		// that have primary class in this package
		//
		Set<UMLclass> importsSet = new HashSet<UMLclass>();
		Map<ViewDefinition, UMLclass> cList = new HashMap<ViewDefinition, UMLclass>();
		for( ViewDefinition vd: this.top.getViews().values()) {
			if( vd.getType() != ViewDefinition.DATA && 
					vd.getType() != ViewDefinition.COLLECTION && 
					vd.getType() != ViewDefinition.LOOKUP) 
				continue;
			UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
			if( c.getPkg().readPackageAddress().contains( addr )) {
				cList.put(vd, c);
			}
			importsSet.addAll(vd.getPrimaryPrimitive().getClasses());
		}
		
		code += "package " + pkg.readPackageAddress() + ".rl.services.serverInteraction\n{\n\n";
		
		code += "	import mx.rpc.AbstractOperation;\n\n";
		
		String s = pkg.getBaseName();
		s = s.substring(0,1).toUpperCase() + s.substring(1,s.length());
		
		code += "\n	public interface I" + s + "Server {\n\n";

		code += "		// ~~~~~~~~~~~~~~~\n";
		code += "		// Count functions\n";
		code += "		// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		function get count" + key.getName() + "():AbstractOperation;\n\n";
		}
		
		code += "		// ~~~~~~~~~~~~~~~~\n";
		code += "		// Insert functions\n";
		code += "		// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			code += "		function get insert" + key.getName() + "():AbstractOperation;\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~\n";
		code += "		// Update functions\n";
		code += "		// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			code += "		function get update" + key.getName() + "():AbstractOperation;\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~\n";
		code += "		// Delete functions\n";
		code += "		// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			code += "		function get delete" + key.getName() + "():AbstractOperation;\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~~~\n";
		code += "		// FindById functions\n";
		code += "		// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		function get find" + key.getName() + "ById():AbstractOperation;\n\n";
		}		

		code += "		// ~~~~~~~~~~~~~~~~~~\n";
		code += "		// Retrieve functions\n";
		code += "		// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		function get retrieve" + key.getName() 
					+ "():AbstractOperation;\n\n";
			code += "		function get retrieve" + key.getName() 
					+ "Paged():AbstractOperation;\n\n";
		}		
		
		code += "		// ~~~~~~~~~~~~~~~\n";
		code += "		// List functions\n";
		code += "		// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		function get list" + key.getName() 
					+ "Paged():AbstractOperation;\n\n";
			code += "		function get list" + key.getName() 
					+ "():AbstractOperation;\n\n";
		}		
		
		return code + "	}\n\n}";		
	}
	
	public String generateServerImplCode(UMLpackage pkg) throws Exception {
		
		String code = "";
		
		String addr = pkg.readPackageAddress();
		
		//
		// search through this package and find all the data views 
		// that have primary class in this package
		//
		Set<UMLclass> importsSet = new HashSet<UMLclass>();
		Map<ViewDefinition, UMLclass> cList = new HashMap<ViewDefinition, UMLclass>();
		for( ViewDefinition vd: this.top.getViews().values()) {
			if( vd.getType() != ViewDefinition.DATA && 
					vd.getType() != ViewDefinition.COLLECTION && 
					vd.getType() != ViewDefinition.LOOKUP) 
				continue;
			UMLclass c = vd.getPrimaryPrimitive().readIdentityClass();
			if( c.getPkg().readPackageAddress().contains( addr )) {
				cList.put(vd, c);
			}
			importsSet.addAll(vd.getPrimaryPrimitive().getClasses());
		}
		
		String s = pkg.getBaseName();
		s = s.substring(0,1).toUpperCase() + s.subSequence(1, s.length());
		
		code += "package " + pkg.readPackageAddress() + ".rl.services.serverInteraction.impl\n{\n\n";
		
		code += "	import " + pkg.readPackageAddress() + ".rl.services.serverInteraction.I" 
				+  s + "Server;\n\n";
		
		code += "	import mx.collections.ArrayCollection;\n";
		code += "	import mx.rpc.AbstractOperation;\n";
		code += "	import mx.rpc.AbstractService;\n";
		code += "	import mx.rpc.AsyncToken;\n";
		code += "	import mx.rpc.events.ResultEvent;\n";
		code += "	import mx.rpc.remoting.RemoteObject;\n";		
		code += "	import mx.rpc.AbstractOperation;\n\n";
		code += "	import edu.isi.bmkeg.utils.dao.Utils;\n\n";
		
		code += "	public class " + s + "ServerImpl \n";
		code += "			extends RemoteObject \n";
		code += "			implements I" + s + "Server\n";
		code += "	{\n\n";

		code += "		private static const SERVICES_DEST:String = \"" 
					+ pkg.getBaseName() + "ServiceImpl\";\n\n";
		
		code += "	public function " + s + "ServerImpl()\n";
		code += "	{\n";
		code += "		destination = SERVICES_DEST;\n";
		code += "		endpoint = Utils.getRemotingEndpoint();\n";
		code += "		showBusyCursor = true;\n";
		code += "	}\n";
		
		code += "		// ~~~~~~~~~~~~~~~\n";
		code += "		// Count functions\n";
		code += "		// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		public function get count" + key.getName() + "():AbstractOperation\n";
			code += "		{\n";
			code += "			return getOperation(\"count" + key.getName() + "\");\n";
			code += "		}\n\n";
		}
		
		code += "		// ~~~~~~~~~~~~~~~~\n";
		code += "		// Insert functions\n";
		code += "		// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			code += "		public function get insert" + key.getName() + "():AbstractOperation\n";
			code += "		{\n";
			code += "			return getOperation(\"insert" + key.getName() + "\");\n";
			code += "		}\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~\n";
		code += "		// Update functions\n";
		code += "		// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			code += "		public function get update" + key.getName() + "():AbstractOperation\n";
			code += "		{\n";
			code += "			return getOperation(\"update" + key.getName() + "\");\n";
			code += "		}\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~\n";
		code += "		// Delete functions\n";
		code += "		// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			code += "		public function get delete" + key.getName() + "():AbstractOperation\n";
			code += "		{\n";
			code += "			return getOperation(\"delete" + key.getName() + "\");\n";
			code += "		}\n\n";
		}

		code += "		// ~~~~~~~~~~~~~~~~~~\n";
		code += "		// FindById functions\n";
		code += "		// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		public function get find" + key.getName() + "ById():AbstractOperation\n";
			code += "		{\n";
			code += "			return getOperation(\"find" + key.getName() + "ById\");\n";
			code += "		}\n\n";
		}		

		code += "		// ~~~~~~~~~~~~~~~~~~\n";
		code += "		// Retrieve functions\n";
		code += "		// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		public function get retrieve" + key.getName() 
					+ "():AbstractOperation\n";
			code += "		{\n";
			code += "			return getOperation(\"retrieve" + key.getName() + "\");\n";
			code += "		}\n\n";
			code += "		public function get retrieve" + key.getName() 
					+ "Paged():AbstractOperation\n";
			code += "		{\n";
			code += "			return getOperation(\"retrieve" + key.getName() + "Paged\");\n";
			code += "		}\n\n";
		}		
		

		code += "		// ~~~~~~~~~~~~~~~\n";
		code += "		// List functions\n";
		code += "		// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			code += "		public function get list" + key.getName() + "():AbstractOperation\n";
			code += "		{\n";
			code += "			return getOperation(\"list" + key.getName() + "\");\n";
			code += "		}\n\n";
			code += "		public function get list" + key.getName() + "Paged():AbstractOperation\n";
			code += "		{\n";
			code += "			return getOperation(\"list" + key.getName() + "Paged\");\n";
			code += "		}\n\n";
		}		
		
		return code + "	}\n\n}";		
	}
	
	
}
