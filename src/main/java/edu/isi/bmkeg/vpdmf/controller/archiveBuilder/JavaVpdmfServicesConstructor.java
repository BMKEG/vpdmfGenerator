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

import edu.isi.bmkeg.uml.model.UMLclass;
import edu.isi.bmkeg.uml.model.UMLpackage;
import edu.isi.bmkeg.utils.Converters;
import edu.isi.bmkeg.utils.mvnRunner.LocalMavenInstall;
import edu.isi.bmkeg.vpdmf.model.definitions.VPDMf;
import edu.isi.bmkeg.vpdmf.model.definitions.ViewDefinition;

public class JavaVpdmfServicesConstructor {

	Logger log = Logger.getLogger("JavaVpdmfInterface");

	private VPDMf top;
	
	public JavaVpdmfServicesConstructor(VPDMf top) throws Exception {
		this.top = top;
	}
	
	public void buildServiceMavenProject(File srcJarFile, File jarFile, 
			String group, String artifactId, String version,
			String repoId, String repoUrl) throws Exception {
				
		if( group == null || group.length() == 0 ) {
			group = "bmkeg.isi.edu";
		}
		
		File targetDir = srcJarFile.getParentFile();

		Map<String, File> filesInSrcJar = new HashMap<String, File>();
		String commandsString = "";

		File tempUnzippedDirectory = Files.createTempDir();
		
		tempUnzippedDirectory.deleteOnExit();
		String dAddr = tempUnzippedDirectory.getAbsolutePath();

		//
		// CREATE A MAVEN PROJECT STEM IN THIS TEMP LOCATION
		// 
		File src = new File(tempUnzippedDirectory.getPath() + "/src");
		src.mkdir();

		File main = new File(tempUnzippedDirectory.getPath() + "/src/main");
		main.mkdir();

		File main_java = new File(tempUnzippedDirectory.getPath() + "/src/main/java");
		main_java.mkdir();

		File main_resources = new File(tempUnzippedDirectory.getPath() + "/src/main/resources");
		main_resources.mkdir();

		File main_resources_vpdmf = new File(tempUnzippedDirectory.getPath() + "/src/main/resources/vpdmf");
		main_resources_vpdmf.mkdir();

		File test = new File(tempUnzippedDirectory.getPath() + "/src/test");
		test.mkdir();
		
		File test_java = new File(tempUnzippedDirectory.getPath() + "/src/test/java");
		test_java.mkdir();

		File test_resources = new File(tempUnzippedDirectory.getPath() + "/src/test/resources");
		test_resources.mkdir();
		
		File target = new File(tempUnzippedDirectory.getPath() + "/target");
		target.mkdir();
		
		//
		// Build Basic pom.xml file
		// 
		String pom = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n";
		pom += "	xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n";
		pom += "	<modelVersion>4.0.0</modelVersion>\n";
		
		pom += "	<groupId>" + group + "</groupId>\n";
		pom += "	<artifactId>" + artifactId + "-services</artifactId>\n";
		pom += "	<version>" + version + "</version>\n";
		pom += "	<packaging>jar</packaging>\n";

		pom += "	<build>\n";
		pom += "		<plugins>\n";
		pom += "			<plugin>\n";
		pom += "				<groupId>org.apache.maven.plugins</groupId>\n";
		pom += "		    	<artifactId>maven-surefire-plugin</artifactId>\n";
		pom += "			    <version>2.12</version>\n";
		pom += "			</plugin>\n";
		pom += "			<plugin>\n";
		pom += "				<groupId>org.apache.maven.plugins</groupId>\n";
		pom += "				<artifactId>maven-source-plugin</artifactId>\n";
		pom += "				<version>2.1.2</version>\n";
		pom += "				<executions>\n";
		pom += "					<execution>\n";
		pom += "						<goals>\n";
		pom += "							<goal>jar</goal>\n";
		pom += "						</goals>\n";
		pom += "					</execution>\n";
		pom += "				</executions>\n";
		pom += "			</plugin>\n";
		pom += "		</plugins>\n";
		pom += "	</build>\n";
		
		pom += "	<distributionManagement>\n";
		if( version.endsWith("-SNAPSHOT") ) {
			pom += "		<snapshotRepository>\n";
			pom += "			<id>" + repoId + "</id>\n";
			pom += "			<name>SNAPSHOT repository</name>\n";
			pom += "			<url>" + repoUrl + "</url>\n";
			pom += "		</snapshotRepository>\n";
		} else {
			pom += "		<repository>\n";
			pom += "			<id>" + repoId + "</id>\n";
			pom += "			<name>Repository</name>\n";
			pom += "			<url>" + repoUrl + "</url>\n";
			pom += "		</repository>\n";
		}
		pom += "	</distributionManagement>\n";
		
		pom += "	<repositories>\n";
		pom += "		<repository>\n";
		pom += "			<id>" + repoId + "</id>\n";
		pom += "			<name>Maven Repository</name>\n";
		pom += "			<url>" + repoUrl + "</url>\n";
		pom += "		</repository>\n";
		pom += "	</repositories>\n";
		pom += "	<dependencies>\n";
		pom += "		<dependency>\n";
		pom += "			<groupId>" + group +"</groupId>\n";
		pom += "			<artifactId>" + artifactId + "-jpa</artifactId>\n";
		pom += "			<version>" + version + "</version>\n";
		pom += "		</dependency>\n";
		pom += "		<dependency>\n";
		pom += "			<groupId>edu.isi.bmkeg</groupId>\n";
		pom += "			<artifactId>vpdmf</artifactId>\n";
		pom += "			<version>[1.1.2-SNAPSHOT,]</version>\n";
		pom += "            <scope>provided</scope>\n";
		pom += "		</dependency>\n";
		pom += "	</dependencies>\n";
		pom += "</project>\n";

		File pomFile = new File(tempUnzippedDirectory.getPath() + "/pom.xml");
		Writer output = new BufferedWriter(new FileWriter(pomFile));
		try {
			output.write(pom);
		} finally {
			output.close();
		}
		filesInSrcJar.put("pom.xml", pomFile);

		for( UMLpackage p: this.top.getUmlModel().listPackages().values() ) {
			if( !p.getBaseName().equals("model") || 
					p.getParent().getBaseName().equals("vpdmf")) {
				continue;
			}
			UMLpackage pp = p.getParent();
			
			String s = pp.getBaseName();
			s = s.substring(0,1).toUpperCase() + s.substring(1, s.length());
			
			String addr = pp.readPackageAddress() + ".services";
			addr = addr.replaceAll("\\.", "/"); 
			File dir = new File( main_java.getPath() + "/" + addr );
			dir.mkdirs();
			File f = new File(dir.getPath() + "/" + s + "Service.java"); 
			String code = this.generateServiceInterfaceCode(pp);
			FileUtils.writeStringToFile(f, code);
			filesInSrcJar.put("/src/main/java/" + addr + "/" + s + "Service.java", f);
			
			addr = pp.readPackageAddress() + ".services.impl";
			addr = addr.replaceAll("\\.", "/"); 
			dir = new File( main_java.getPath() + "/" + addr );
			dir.mkdirs();
			f = new File(dir.getPath() + "/" + s + "ServiceImpl.java"); 
			code = this.generatServiceCode(pp);
			FileUtils.writeStringToFile(f, code);
			filesInSrcJar.put("/src/main/java/" + addr + "/" + s + "ServiceImpl.java", f);
			
			addr = pp.readPackageAddress() + ".dao";
			addr = addr.replaceAll("\\.", "/"); 
			dir = new File( main_java.getPath() + "/" + addr );
			dir.mkdirs();
			f = new File(dir.getPath() + "/" + s + "Dao.java"); 
			code = this.generateDaoInterfaceCode(pp);
			FileUtils.writeStringToFile(f, code);
			filesInSrcJar.put("/src/main/java/" + addr + "/" + s + "Dao.java", f);
						
			addr = pp.readPackageAddress() + ".dao.impl";
			addr = addr.replaceAll("\\.", "/"); 
			dir = new File( main_java.getPath() + "/" + addr );
			dir.mkdirs();
			f = new File(dir.getPath() + "/" + s + "DaoImpl.java"); 
			code = this.generatDaoCode(pp);
			FileUtils.writeStringToFile(f, code);
			filesInSrcJar.put("/src/main/java/" + addr + "/" + s + "DaoImpl.java", f);
							
		}
		
		Converters.jarIt(filesInSrcJar, srcJarFile);
		
		if(jarFile == null)
			return;
		
		//
		// Use maven to compile the code 
		//

		String out = LocalMavenInstall.runMavenCommand("package -f " + pomFile.getAbsolutePath());
		log.debug(out);
		        
        File f1 = new File(dAddr + "/target/" + artifactId + "-services-"+ version + ".jar" );
        
        if( !f1.exists() )
        	throw new Exception("Build for " + f1.getName() + " failed. Check source");
        
        InputStream from = new FileInputStream(f1);
        OutputStream to = new FileOutputStream(jarFile);

        byte[] buff = new byte[1024];
        int len;
        while ((len = from.read(buff)) > 0) {
        	to.write(buff, 0, len);
        }
        from.close();
        to.close();

		Converters.recursivelyDeleteFiles(tempUnzippedDirectory);

	}
	
	private String generateServiceInterfaceCode(UMLpackage pkg) throws Exception {
		
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
		
		code += "package " + pkg.readPackageAddress() + ".services;\n\n";
		
		for( UMLclass impStr: importsSet) {
			
			String cAddr = impStr.readClassAddress();
			code += "import " + cAddr + ";\n";
			
			cAddr = cAddr.replaceAll("\\.model\\.", ".model.qo.");
			cAddr += "_qo";
			code += "import " + cAddr + ";\n";
			
		}
		code += "import java.util.List;\n\n";
		code += "import edu.isi.bmkeg.vpdmf.model.instances.LightViewInstance;\n\n";

		String s = pkg.getBaseName();
		s = s.substring(0,1).toUpperCase() + s.substring(1, s.length());
		code += "public interface " + s + "Service {\n\n";

		code += "	// ~~~~~~~~~~~~~~~\n";
		code += "	// Count functions\n";
		code += "	// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			String s2 = c.getBaseName();
			s2 = s2.substring(0,1).toLowerCase() + s2.substring(1, s2.length());
			code += "	public int count" + key.getName() + "(" 
					+ c.getBaseName() + "_qo " + s2 + ") throws Exception;\n\n";
		}
		
		code += "	// ~~~~~~~~~~~~~~~~\n";
		code += "	// Insert functions\n";
		code += "	// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "	public long insert" + key.getName() + "(" 
					+ c.getBaseName() + " " + s + ") throws Exception;\n\n";
		}

		code += "	// ~~~~~~~~~~~~~~~~\n";
		code += "	// Update functions\n";
		code += "	// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "	public long update" + key.getName() + "(" 
					+ c.getBaseName() + " " + s + ") throws Exception;\n\n";
		}

		code += "	// ~~~~~~~~~~~~~~~~\n";
		code += "	// Delete functions\n";
		code += "	// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			code += "	public void delete" + key.getName() 
					+ "(long id) throws Exception;\n\n";
		}

		code += "	// ~~~~~~~~~~~~~~~~~~\n";
		code += "	// FindById functions\n";
		code += "	// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "	public " + c.getBaseName() + " find" + key.getName() 
					+ "ById(long id) throws Exception;\n\n";			
		}		

		code += "	// ~~~~~~~~~~~~~~~~~~\n";
		code += "	// Retrieve functions\n";
		code += "	// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "	public List<" + c.getBaseName() + "> retrieve" + key.getName() 
					+ "Paged(" + c.getBaseName() + " o, int offset, int cnt) throws Exception;\n\n";			
			code += "	public List<" + c.getBaseName() + "> retrieve" + key.getName() 
					+ "(" + c.getBaseName() + " o) throws Exception;\n\n";			
		}		
		
		code += "	// ~~~~~~~~~~~~~~~\n";
		code += "	// List functions\n";
		code += "	// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "	public List<LightViewInstance> list" + key.getName() 
					+ "Paged(" + c.getBaseName() + "_qo o, int offset, int cnt) throws Exception;\n\n";			
			code += "	public List<LightViewInstance> list" + key.getName() 
					+ "(" + c.getBaseName() + "_qo o) throws Exception;\n\n";			
		}		
		
		return code + "}";
		
	}

	private String generatServiceCode(UMLpackage pkg) throws Exception {
		
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
		
		code += "package " + pkg.readPackageAddress() + ".services.impl;\n\n";
		
		code += "import java.util.List;\n\n";
		code += "import org.apache.log4j.Logger;\n";
		code += "import org.springframework.beans.factory.annotation.Autowired;\n";
		code += "import org.springframework.flex.remoting.RemotingDestination;\n";
		code += "import org.springframework.stereotype.Service;\n";
		code += "import org.springframework.transaction.annotation.Transactional;\n";
		code += "import org.springframework.util.Assert;\n\n";

		for( UMLclass impStr: importsSet) {
			String cAddr = impStr.readClassAddress();
			code += "import " + cAddr + ";\n";
			
			cAddr = cAddr.replaceAll("\\.model\\.", ".model.qo.");
			cAddr += "_qo";
			code += "import " + cAddr + ";\n";
		}
		
		code += "import edu.isi.bmkeg.vpdmf.model.instances.LightViewInstance;\n\n";
		code += "import " +  pkg.readPackageAddress() + ".services.*;\n";
		code += "import " +  pkg.readPackageAddress() + ".dao.*;\n";
		
		code += "@RemotingDestination\n";
		code += "@Transactional\n";
		code += "@Service\n";
		code += "public class " + pTitle + "ServiceImpl implements " + pTitle + "Service {\n\n";

		code += "	private static final Logger logger = Logger.getLogger(" + pTitle + "ServiceImpl.class);\n\n"; 
		
		code += "	@Autowired\n";
		String daoName = pkg.getBaseName() + "Dao";
		code += "	private " + pTitle + "Dao " +  daoName + ";\n\n";
				
		code += "	public void set" + daoName + "(" + pTitle + "Dao " + daoName + ") {\n";
		code += "		this." + daoName + " = " + daoName + ";\n	}\n\n";

		code += "	// ~~~~~~~~~~~~~~~\n";
		code += "	// Count functions\n";
		code += "	// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			String s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "	public int count" + key.getName() + "(" 
					+ c.getBaseName() + "_qo " + s + ") throws Exception {\n";
			code += "		return " + daoName + ".count" + key.getName() + "(" 
					+ s + ");\n";
			code += "	}\n\n";
		}
		
		code += "	// ~~~~~~~~~~~~~~~~\n";
		code += "	// Insert functions\n";
		code += "	// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			String s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "	public long insert" + key.getName() + "(" 
					+ c.getBaseName() + " " + s + ") throws Exception {\n";
			code += "		return " + daoName + ".insert" + key.getName() + "(" + s + ");\n";
			code += "	}\n\n";
		}

		code += "	// ~~~~~~~~~~~~~~~~\n";
		code += "	// Update functions\n";
		code += "	// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			String s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "	public long update" + key.getName() + "(" 
					+ c.getBaseName() + " " + s + ") throws Exception {\n";
			code += "		return " + daoName + ".update" + key.getName() + "(" + s + ");\n";
			code += "	}\n\n";
		}

		code += "	// ~~~~~~~~~~~~~~~~\n";
		code += "	// Delete functions\n";
		code += "	// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			code += "	public void delete" + key.getName() 
					+ "(long id) throws Exception {\n";
			code += "		" + daoName + ".delete" + key.getName() + "(id);\n";
			code += "	}\n\n";			
		}

		code += "	// ~~~~~~~~~~~~~~~~~~\n";
		code += "	// FindById functions\n";
		code += "	// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "	public " + c.getBaseName() + " find" + key.getName() 
					+ "ById(long id) throws Exception {\n";			
			code += "		return " + daoName + ".find" + key.getName() + "ById(id);\n";
			code += "	}\n\n";			
		}		

		code += "	// ~~~~~~~~~~~~~~~~~~\n";
		code += "	// Retrieve functions\n";
		code += "	// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "	public List<" + c.getBaseName() + "> retrieve" + key.getName() 
					+ "Paged(" + c.getBaseName() + " o, int offset, int cnt) throws Exception { \n";	
			code += "		List<" + c.getBaseName() + "> data = " + daoName + ".retrieve" 
					+ key.getName() + "Paged(o, offset, cnt);\n";
			code += "		return data;\n";
			code += "	}\n\n";

			code += "	public List<" + c.getBaseName() + "> retrieve" + key.getName() 
					+ "(" + c.getBaseName() + " o) throws Exception { \n";	
			code += "		List<" + c.getBaseName() + "> data = " + daoName + ".retrieve" 
					+ key.getName() + "(o);\n";
			code += "		return data;\n";
			code += "	}\n\n";
		
		}		

		code += "	// ~~~~~~~~~~~~~~~\n";
		code += "	// List functions\n";
		code += "	// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "	public List<LightViewInstance> list" + key.getName() 
					+ "Paged(" + c.getBaseName() + "_qo o, int offset, int cnt) throws Exception {\n";			
			code += "		List<LightViewInstance> data = " + daoName + ".list" 
					+ key.getName() + "Paged(o, offset, cnt);\n";
			code += "		return data;\n";
			code += "	}\n\n";

			code += "	public List<LightViewInstance> list" + key.getName() 
					+ "(" + c.getBaseName() + "_qo o) throws Exception {\n";			
			code += "		List<LightViewInstance> data = " + daoName + ".list" 
					+ key.getName() + "(o);\n";
			code += "		return data;\n";
			code += "	}\n\n";
		}		
		
		return code + "}";
		
	}

	private String generateDaoInterfaceCode(UMLpackage pkg) throws Exception {
		
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
		
		code += "package " + pkg.readPackageAddress() + ".dao;\n\n";
		
		for( UMLclass impStr: importsSet) {
		
			String cAddr = impStr.readClassAddress();
			code += "import " + cAddr + ";\n";
			
			cAddr = cAddr.replaceAll("\\.model\\.", ".model.qo.");
			cAddr += "_qo";
			code += "import " + cAddr + ";\n";
		
		}
		
		code += "import java.io.File;\n\n";
		code += "import java.util.List;\n";
		code += "import java.util.Map;\n";
		code += "import java.util.Set;\n\n";
		code += "import edu.isi.bmkeg.vpdmf.model.instances.LightViewInstance;\n";
		code += "import edu.isi.bmkeg.vpdmf.dao.CoreDao;\n\n";
		
		String s = pkg.getBaseName();
		s = s.substring(0,1).toUpperCase() + s.substring(1, s.length());
		code += "public interface " + s + "Dao {\n\n";
		code += "	public void setCoreDao(CoreDao coreDao);\n\n";
		code += "	public CoreDao getCoreDao();\n\n";
		
		code += "	// ~~~~~~~~~~~~~~~\n";
		code += "	// Count functions\n";
		code += "	// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			String s2 = c.getBaseName();
			code += "	public int count" + key.getName() + "(" 
					+ c.getBaseName() + "_qo " + s2 + ") throws Exception;\n\n";
		}
		
		code += "	// ~~~~~~~~~~~~~~~~\n";
		code += "	// Insert functions\n";
		code += "	// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "	public long insert" + key.getName() + "(" 
					+ c.getBaseName() + " " + s + ") throws Exception;\n\n";
		}

		code += "	// ~~~~~~~~~~~~~~~~\n";
		code += "	// Update functions\n";
		code += "	// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "	public long update" + key.getName() + "(" 
					+ c.getBaseName() + " " + s + ") throws Exception;\n\n";
		}

		code += "	// ~~~~~~~~~~~~~~~~\n";
		code += "	// Delete functions\n";
		code += "	// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			code += "	public void delete" + key.getName() 
					+ "(long id) throws Exception;\n\n";
		}

		code += "	// ~~~~~~~~~~~~~~~~~~\n";
		code += "	// FindById functions\n";
		code += "	// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "	public " + c.getBaseName() + " find" + key.getName() 
					+ "ById(long id) throws Exception;\n\n";			
		}		

		code += "	// ~~~~~~~~~~~~~~~~~~\n";
		code += "	// Retrieve functions\n";
		code += "	// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "	public List<" + c.getBaseName() + "> retrieve" + key.getName() 
					+ "Paged(" + c.getBaseName() + " o, int offset, int cnt) throws Exception;\n\n";			
			code += "	public List<" + c.getBaseName() + "> retrieve" + key.getName() 
					+ "(" + c.getBaseName() + " o) throws Exception;\n\n";			
		}		
		

		code += "	// ~~~~~~~~~~~~~~~\n";
		code += "	// List functions\n";
		code += "	// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "	public List<LightViewInstance> list" + key.getName() 
					+ "Paged(" + c.getBaseName() + "_qo o, int offset, int cnt) throws Exception;\n\n";			
			code += "	public List<LightViewInstance> list" + key.getName() 
					+ "(" + c.getBaseName() + "_qo o) throws Exception;\n\n";			
		}		
		
		return code + "}";
		
	}
	
	private String generatDaoCode(UMLpackage pkg) throws Exception {
		
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
		String daoName = pkg.getBaseName() + "Dao";
		
		code += "package " + pkg.readPackageAddress() + ".dao.impl;\n\n";
		
		code += "import java.io.File;\n";
		code += "import java.util.ArrayList;\n";
		code += "import java.util.Collections;\n";
		code += "import java.util.HashMap;\n";
		code += "import java.util.Iterator;\n";
		code += "import java.util.List;\n";
		code += "import java.util.Map;\n";
		code += "import java.util.Set;\n";

		code += "import org.apache.log4j.Logger;\n";
		code += "import org.springframework.beans.factory.annotation.Autowired;\n";
		code += "import org.springframework.stereotype.Repository;\n";
		code += "import edu.isi.bmkeg.uml.model.UMLclass;\n";
		code += "import edu.isi.bmkeg.utils.Converters;\n";
		code += "import edu.isi.bmkeg.vpdmf.controller.queryEngineTools.ChangeEngine;\n";
		code += "import edu.isi.bmkeg.vpdmf.controller.queryEngineTools.VPDMfChangeEngineInterface;\n";
		code += "import edu.isi.bmkeg.vpdmf.dao.CoreDao;\n";
		code += "import edu.isi.bmkeg.vpdmf.model.definitions.*;\n";
		code += "import edu.isi.bmkeg.vpdmf.model.instances.*;\n";

		code += "import " + pkg.readPackageAddress() + ".dao.*;\n";
		
		for( UMLclass impStr: importsSet) {			
			String cAddr = impStr.readClassAddress();
			code += "import " + cAddr + ";\n";
			
			cAddr = cAddr.replaceAll("\\.model\\.", ".model.qo.");
			cAddr += "_qo";
			code += "import " + cAddr + ";\n";
		}
	
		code += "@Repository\n";
		code += "public class " + pTitle + "DaoImpl implements " + pTitle + "Dao {\n\n";

		code += "	private static final Logger logger = Logger.getLogger(" + pTitle + "DaoImpl.class);\n\n"; 
		
		code += "	@Autowired\n";
		code += "	private CoreDao coreDao;\n\n";
				
		code += "	// ~~~~~~~~~~~~\n";
		code += "	// Constructors\n";
		code += "	// ~~~~~~~~~~~~\n\n";
		code += "	public " + pTitle + "DaoImpl() throws Exception {}\n\n";
		code += "	public " + pTitle + "DaoImpl(CoreDao coreDao) throws Exception {\n";
		code += "		this.coreDao = coreDao;\n";
		code += "	}\n\n";

		code += "	// ~~~~~~~~~~~~~~~~~~~\n";
		code += "	// Getters and Setters\n";
		code += "	// ~~~~~~~~~~~~~~~~~~~\n\n";
		code += "	public void setCoreDao(CoreDao dlVpdmf) {\n";
		code += "		this.coreDao = dlVpdmf;\n";
		code += "	}\n\n";
		code += "	public CoreDao getCoreDao() {\n";
		code += "		return coreDao;\n";
		code += "	}\n\n";
		
		code += "	private VPDMfChangeEngineInterface getCe() {\n";
		code += "		return coreDao.getCe();\n";
		code += "	}\n";
		code += "	private VPDMf getTop() {\n";
		code += "		return coreDao.getTop();\n";
		code += "	}\n\n";
			
		code += "	// ~~~~~~~~~~~~~~~\n";
		code += "	// Count functions\n";
		code += "	// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			String s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "	public int count" + key.getName() + "(" 
					+ c.getBaseName() + "_qo " + s + ") throws Exception {\n";
			code += "		return getCoreDao().countView(" + s + ",\"" + key.getName() + "\");\n";
			code += "	}\n\n";
		}
		
		code += "	// ~~~~~~~~~~~~~~~~\n";
		code += "	// Insert functions\n";
		code += "	// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			String s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "	public long insert" + key.getName() + "(" 
					+ c.getBaseName() + " " + s + ") throws Exception {\n";
			code += "		return getCoreDao().insert(" + s + ", \"" + key.getName() + "\");\n";
			code += "	}\n\n";
		}

		code += "	// ~~~~~~~~~~~~~~~~\n";
		code += "	// Update functions\n";
		code += "	// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			String s = c.getBaseName();
			s = s.substring(0,1).toLowerCase() + s.substring(1, s.length());
			code += "	public long update" + key.getName() + "(" 
					+ c.getBaseName() + " " + s + ") throws Exception {\n";
			code += "		return getCoreDao().update(" + s + ", \"" + key.getName() + "\");\n";
			code += "	}\n\n";
		}

		code += "	// ~~~~~~~~~~~~~~~~\n";
		code += "	// Delete functions\n";
		code += "	// ~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			if( key.getType() == ViewDefinition.LOOKUP )
				continue;
			UMLclass c = cList.get(key);
			code += "	public void delete" + key.getName() 
					+ "(long id) throws Exception {\n\n";
			code += "		try {\n";
			code += "			getCe().connectToDB();\n";
			code += "			getCe().turnOffAutoCommit();\n";
			code += "			getCe().deleteView(\"" + key.getName() +"\", id );\n";
			code += "		} catch (Exception e) {\n";
			code += "			throw e;\n";
			code += "		} finally {\n";
			code += "			getCe().closeDbConnection();\n";
			code += "		}\n\n";
			code += "	}\n\n";			
		}

		code += "	// ~~~~~~~~~~~~~~~~~~\n";
		code += "	// FindById functions\n";
		code += "	// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "	public " + c.getBaseName() + " find" + key.getName() 
					+ "ById(long id) throws Exception {\n\n";		
			
			code += "		" + c.getBaseName() + " o = (" + c.getBaseName() + ") getCoreDao()"+
					".findById(id, new " + c.getBaseName() + "(), \"" + key.getName() + "\");\n\n";			
			code += "		return o;\n\n";
			code += "	}\n\n";			
		}		

		code += "	// ~~~~~~~~~~~~~~~~~~\n";
		code += "	// Retrieve functions\n";
		code += "	// ~~~~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "	public List<" + c.getBaseName() + "> retrieve" + key.getName() 
					+ "Paged(" + c.getBaseName() + " o, int offset, int cnt) throws Exception { \n\n";	
			code += "		List<" + c.getBaseName() + "> data = getCoreDao()"  
					+ ".retrieve(o, \"" + key.getName() + "\", offset, cnt);\n";
			code += "		return data;\n";
			code += "	}\n\n";
			
			code += "	public List<" + c.getBaseName() + "> retrieve" + key.getName() 
					+ "(" + c.getBaseName() + " o) throws Exception { \n\n";	
			code += "		List<" + c.getBaseName() + "> data = getCoreDao()" 
					+ ".retrieve(o, \"" + key.getName() + "\");\n";
			code += "		return data;\n";
			code += "	}\n\n";
			
		}		
		
		code += "	// ~~~~~~~~~~~~~~~\n";
		code += "	// List functions\n";
		code += "	// ~~~~~~~~~~~~~~~\n\n";
		for( ViewDefinition key: cList.keySet()) {
			UMLclass c = cList.get(key);
			code += "	public List<LightViewInstance> list" + key.getName() 
					+ "Paged(" + c.getBaseName() + "_qo o, int offset, int cnt) throws Exception { \n\n";	
			code += "		List<LightViewInstance> data = getCoreDao()" 
					+ ".list(o, \"" + key.getName() + "\", offset, cnt);\n";
			code += "		return data;\n";
			code += "	}\n\n";
			
			code += "	public List<LightViewInstance> list" + key.getName() 
					+ "(" + c.getBaseName() + "_qo o) throws Exception { \n\n";	
			code += "		List<LightViewInstance> data = getCoreDao()" 
					+ ".list(o, \"" + key.getName() + "\");\n";
			code += "		return data;\n";
			code += "	}\n\n";
		}		
		
		return code + "}";
		
	}
		
}
