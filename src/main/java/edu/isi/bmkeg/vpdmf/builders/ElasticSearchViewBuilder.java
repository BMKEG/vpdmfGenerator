package edu.isi.bmkeg.vpdmf.builders;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import com.google.common.io.Files;

import edu.isi.bmkeg.uml.builders.ImplConvert;
import edu.isi.bmkeg.uml.builders.JavaPojoUmlBuilder;
import edu.isi.bmkeg.uml.builders.UmlComponentBuilder;
import edu.isi.bmkeg.uml.model.UMLattribute;
import edu.isi.bmkeg.uml.model.UMLclass;
import edu.isi.bmkeg.uml.model.UMLmodel;
import edu.isi.bmkeg.uml.model.UMLpackage;
import edu.isi.bmkeg.uml.model.UMLrole;
import edu.isi.bmkeg.utils.Converters;
import edu.isi.bmkeg.utils.MapCreate;
import edu.isi.bmkeg.utils.mvnRunner.LocalMavenInstall;
import edu.isi.bmkeg.utils.superGraph.SuperGraphNode;
import edu.isi.bmkeg.vpdmf.model.definitions.PrimitiveDefinition;
import edu.isi.bmkeg.vpdmf.model.definitions.VPDMf;
import edu.isi.bmkeg.vpdmf.model.definitions.ViewDefinition;
import edu.isi.bmkeg.vpdmf.model.definitions.specs.PrimitiveLinkSpec;
import edu.isi.bmkeg.vpdmf.model.definitions.specs.PrimitiveSpec;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;

/**
 * Builds a framework for views that can be accessed by Spring Data Elasticsearch
 * 
 * @author Gully
 *
 */
public class ElasticSearchViewBuilder extends UmlComponentBuilder implements ImplConvert {

	Logger log = Logger.getLogger("edu.isi.bmkeg.vpdmf.builders.ElasticSearchViewBuilder");

	private boolean annotFlag = true;
	private ClassPool pool = ClassPool.getDefault();

	private Map<String, String> queryObjectLookupTable;
	private Map<String, String> javaLookupTable;

	private Set<UMLclass> classesToIndex = new HashSet<UMLclass>();

	private boolean buildQuestions = false;
	
	private JavaPojoUmlBuilder javaBuilder;

	private VPDMf top;

	protected static String[] javaTargetTypes = new String[] { "long", "byte", "short", "int", "long", "float",
			"double", "boolean", "char", "String", "String", "String", "byte[]", "Object", "java.util.Date",
			"java.util.Date", "URL" };

	protected static String[] javaQuestionTargetTypes = new String[] { "String", "String", "String", "String", "String",
			"String", "String", "String", "String", "String", "String", "String", "String", "String", "String",
			"String", "String" };

	public ElasticSearchViewBuilder(VPDMf top) throws Exception {
		super();
		this.buildLookupTable();
		this.top = top;
		this.setUmlModel(top.getUmlModel());
		this.javaBuilder = new JavaPojoUmlBuilder();
		this.javaBuilder.setUmlModel(top.getUmlModel());
	}

	public void buildLookupTable() throws Exception {

		javaLookupTable = new HashMap<String, String>(
				MapCreate.asMap(UmlComponentBuilder.baseAttrTypes, javaTargetTypes));

		queryObjectLookupTable = new HashMap<String, String>(
				MapCreate.asMap(UmlComponentBuilder.baseAttrTypes, javaQuestionTargetTypes));

		this.setLookupTable(javaLookupTable);

	}

	public Map<String, File> generateJavaCodeForModel(File dumpDir, String pkgPattern, boolean annotFlag)
			throws Exception {

		this.annotFlag = annotFlag;

		this.getUmlModel().cleanModel();

		Map<String, File> filesInZip = new HashMap<String, File>();

		this.convertAttributes();
		
		this.getUmlModel().convertAllItemsFromDashToCamelCase();

		String dAddr = dumpDir.getAbsolutePath();

		List<String> keys = new ArrayList<String>(
				this.getUmlModel().listPackages(pkgPattern).keySet()
				);

		for(ViewDefinition vd : top.getViews().values() ) {
			if( top.getViewsToIndex().contains(vd.getName()) )
				this.getClassesToIndex().add(vd.getPrimaryPrimitive().readIdentityClass());			
		}
		
		List<UMLpackage> pkgs = new ArrayList<UMLpackage>();
		for( UMLpackage p : this.getUmlModel().listPackages().values()) {
			if( p.getUri() != null ) {
				UMLpackage p2 = this.getUmlModel().moveToSubPackage(p, "esViews");
				pkgs.add(p2);
			}
		}
		
		Collections.sort(keys);

		/**
		 * For each view, construct a mini-model that can serve 
		 * the basis of a separate Spring Data representation.
		 * 
		 * Generate the source code for each of these views separately
		 * in separate packages as we go. 
		 * 
		 */
		for(String viewName: this.top.getViewsToIndex() ) {
			
			ViewDefinition vd = this.top.getViews().get(viewName);

			Set<String> classRoles = new HashSet<String>();
			List<PrimitiveSpec> psList = vd.getSpec().getPrimitives();
			for(PrimitiveSpec ps : psList) {
				for(PrimitiveLinkSpec pls : ps.getPvLinks()) {
					classRoles.add( pls.getC1() + "." + pls.getRole() );
				}
			}
			
			List<UMLpackage> movedPkgs = new ArrayList<UMLpackage>();
			for(UMLpackage p : pkgs) {
				Map<String, UMLclass> m = this.getUmlModel().listClasses(p.readPackageAddress());
				for(UMLclass c : m.values()) {
					c.setImplName(vd.getName() + "__" + c.getBaseName());
				}	
				movedPkgs.add(
						this.getUmlModel().moveToSubPackage(p, vd.getName())
						);				
			}
			
			for(SuperGraphNode n : vd.getSubGraph().getNodes().values() ) {
				PrimitiveDefinition pd = (PrimitiveDefinition) n;
				
				for(UMLclass c : pd.getClasses() ) {
				
					// Check to see if the class is a set backing table...
					// if so don't generate the source code.
					if (c.getStereotype() != null && c.getStereotype().equals("Link")) 
						continue;
					
					// If the class is a type, then don't generate source code
					if (this.getLookupTable().containsKey(c.getImplName())) 
						continue;
				
					String addr = c.readClassAddress();
					String fAddr = addr.replaceAll("\\.", "/");
	
					List<UMLrole> deactiveatedRoles = new ArrayList<UMLrole>();
					for( UMLrole r : c.getAssociateRoles().values() ) {
						if( !classRoles.contains(c.getBaseName() + "." + r.getBaseName()) &&
								r.getNavigable()){
							deactiveatedRoles.add(r);
							r.setToImplement(false);
						}
					}
					
					String code = this.generateCodeForClass(vd, c, pkgPattern);
					
					for( UMLrole r : deactiveatedRoles ) {
						r.setToImplement(true);
					}
	
					File f = new File(dAddr + "/" + fAddr + ".java");
					File dir = f.getParentFile();
					dir.mkdirs();
					
					FileUtils.writeStringToFile(f, code);
					filesInZip.put(fAddr + ".java", f);
				}

			}			
			
			for(UMLpackage p : movedPkgs) {
				Map<String, UMLclass> m = this.getUmlModel().listClasses(p.readPackageAddress());
				for(UMLclass c : m.values()) {
					c.setImplName(c.getImplName());
				}	
				this.getUmlModel().moveBackFromSubPackage(p);				
			}

		}

		return filesInZip;

	}

	/**
	 * Generates an java entity model source code file
	 * @param vd 
	 * 
	 * @param c
	 *            - the class being generated from.
	 * @return
	 * @throws Exception
	 */
	protected String generateCodeForClass(ViewDefinition vd, UMLclass c, String pkgPattern) throws Exception {

		String code = "";

		String addr = c.getPkg().getPkgAddress();

		try {
			addr = addr.substring(2, addr.length());
		} catch (StringIndexOutOfBoundsException e) {
			// top level package, ignore the error
		}
		code += "package " + addr + ";\n\n";

		code += generateImportStatements(c, pkgPattern);

		code += "\nimport java.util.*;\n";
		code += "import org.springframework.data.annotation.*;\n";
		code += "import org.springframework.data.elasticsearch.annotations.*;\n";
		code += "import static org.springframework.data.elasticsearch.annotations.FieldIndex.*;\n\n";
		code += "import lombok.Data;\n\n";
		//code += "import lombok.Builder;\n\n";
		
		if (c.getDocumentation() != null && c.getDocumentation().length() > 0) {
			code += this.commentOut(c.getDocumentation(), 0);
		}

		//code += "@Builder\n";
		code += "@Data\n";
		List<ViewDefinition> ptVds = vd.readAllParents(); 	
		String indexName = vd.getName();
		if( ptVds.size() > 0 ) 
			indexName = ptVds.get(ptVds.size()-1).getName();
		
		if (vd.getPrimaryPrimitive().readIdentityClass().equals(c)) {
			code += "@Document(indexName = \"" + indexName.toLowerCase() + "-index\", " + "type = \""
					+ vd.getName().toLowerCase() + "\", " + "shards = 1, replicas = 0, "
					+ "refreshInterval = \"-1\")\n";
		}

		code += "public class " + c.getImplName();

		if (c.getParent() != null) {
			code += " extends " + c.getParent().getImplName();
		}

		code += " {\n";

		for (UMLattribute a : c.getAttributes()) {
			
			if (a.getFkRole() != null) {
				continue;
			}

			if (!a.getToImplement())
				continue;

			if (a.getDocumentation() != null && a.getDocumentation().length() > 0)
				code += this.commentOut(a.getDocumentation(), 1);

			if (a.getStereotype() != null && a.getStereotype().equals("PK")) {
				code += "	@Id\n";
			}
			code += this.generateAttrDeclaration(a) + "\n";
		}

		List<String> roleKeys = new ArrayList<String>(c.getAssociateRoles().keySet());
		Collections.sort(roleKeys);
		for (String key : roleKeys) {
			UMLrole r = c.getAssociateRoles().get(key);

			if (!r.getNavigable())
				continue;

			if (!r.getToImplement())
				continue;

			if (r.getDocumentation() != null && r.getDocumentation().length() > 0)
				code += this.commentOut(r.getDocumentation(), 1);

			code += "	@Field(type = FieldType.Nested)\n";
			code += this.generateAttrDeclaration(r) + "\n";

		}

		code += "\n	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n\n";
		
		
		code += "	public " + c.getImplName() + "() {}\n";
		
		String sig = generateFullClassSignature(c);
		if( sig.length() > 0 ) {
			code += "	public " + c.getImplName() + "(";
			code += sig;
			code += ") {\n";
				
			for (UMLattribute a : c.getAttributes()) {
				if (a.getFkRole() != null) continue;
				if (!a.getToImplement()) continue;
				code += "		this." + a.getImplName() + " = " + a.getImplName() + ";\n";
			}
			for (String key : roleKeys) {
				UMLrole r = c.getAssociateRoles().get(key);
				if (!r.getToImplement()) continue;
				if (!r.getNavigable()) continue;
				code += "		this." + r.getImplName() + " = " + r.getImplName() + ";\n";
			}
			code += "	}\n";
		}

		code += "\n\n}\n";

		return code;

	}

	private String generateFullClassSignature(UMLclass c) {
		
		List<String> roleKeys = new ArrayList<String>(c.getAssociateRoles().keySet());
		Collections.sort(roleKeys);

		String sig = "";
				
		for (UMLattribute a : c.getAttributes()) {
			if (a.getFkRole() != null) continue;
			if (!a.getToImplement()) continue;
			if(sig.length() > 0) sig += ", ";
			sig += a.getType().getImplName() + " " + a.getImplName();
		}
		
		for (String key : roleKeys) {
			UMLrole r = c.getAssociateRoles().get(key);
			if (!r.getToImplement()) continue;
			if (!r.getNavigable()) continue;
			if( sig.length() > 0 ) sig += ", ";
			if( r.getMult_upper() == -1 )	
				sig += "List<" + r.getDirectClass().getImplName() + "> " + 
						r.getImplName();
			else
				sig += r.getDirectClass().getImplName() + " " + 
						r.getImplName();
		}
				
		return sig;
	
	}

	private String generateAttrDeclaration(UMLattribute a) {
		String code = "";

		code += "	private " + a.getType().getImplName() + " " + a.getImplName() + ";\n";

		return code;

	}

	private String generateAttrDeclaration(UMLrole r) {
		String code = "";

		if (r.getMult_upper() != -1) {
			code += "	private " + r.getDirectClass().getImplName() + " " + r.getImplName() + ";\n";
		}
		// set backing tables
		else if (r.getImplementz() != null) {
			UMLrole rr = r.getImplementz();
			code += "	private List<" + rr.getDirectClass().getImplName() + "> " + r.getImplName()
					+ " = new ArrayList<" + rr.getDirectClass().getImplName() + ">();\n";

		} else {
			code += "	private List<" + r.getDirectClass().getImplName() + "> " + r.getImplName() + " = new ArrayList<"
					+ r.getDirectClass().getImplName() + ">();\n";
		}

		return code;

	}

	private String generateNonGenericsAttrDeclaration(UMLrole r) {
		String code = "";

		if (r.getMult_upper() != -1) {
			code += "	private " + r.getDirectClass().readClassAddress() + " " + r.getImplName() + ";\n";
		} else {
			code += "	private java.util.List " + r.getImplName() + " = new java.util.ArrayList();\n";
		}

		return code;

	}

	private String generateJPASetter(UMLattribute a) {

		String code = "";

		String s = a.getImplName();

		code += "	public void set" + this.generateStemString(a) + "(" + a.getType().getImplName() + " "
				+ a.getImplName() + ") {\n";

		code += "		this." + s + " = " + s + ";\n";

		code += "	}";

		return code;
	}

	private String generateJPAGetter(UMLattribute a) throws Exception {
		String code = "";

		String s = a.getImplName();

		code += "	public " + a.getType().getImplName() + " get" + this.generateStemString(a) + "() {\n";

		code += "		return this." + s + ";\n";

		code += "	}";

		return code;

	}

	private String generateJPASetter(UMLrole r) {

		String code = "";

		String s = r.getImplName();

		code += "	public void set" + this.generateStemString(r) + "(";

		if (r.getMult_upper() == -1 && r.getImplementz() == null) {
			code += "List<" + r.getDirectClass().getImplName() + "> " + r.getImplName() + ") {\n";
		} else if (r.getImplementz() != null) {
			code += "List<" + r.getImplementz().getDirectClass().getImplName() + "> " + r.getImplName() + ") {\n";
		} else {
			code += r.getDirectClass().getImplName() + " " + r.getImplName() + ") {\n";
		}

		code += "		this." + s + " = " + s + ";\n";

		code += "	}";

		return code;
	}

	private String generateJPAGetter(UMLrole r) throws Exception {
		String code = "";

		String s = r.getImplName();

		UMLrole or = r.otherRole();

		code += "	public ";

		if (r.getMult_upper() == -1 && r.getImplementz() == null) {
			code += "List<" + r.getDirectClass().getImplName() + ">";
		} else if (r.getImplementz() != null) {
			code += "List<" + r.getImplementz().getDirectClass().getImplName() + ">";
		} else {
			code += r.getDirectClass().getImplName();
		}

		code += " get" + this.generateStemString(r) + "() {\n";

		code += "		return this." + s + ";\n";

		code += "	}";

		return code;

	}

	private String generateStemString(UMLattribute a) {
		String s = a.getImplName();

		return s.substring(0, 1).toUpperCase() + s.substring(1, s.length());
	}

	private String generateStemString(UMLrole r) {
		String s = r.getImplName();

		return s.substring(0, 1).toUpperCase() + s.substring(1, s.length());

	}

	public void constructClassPool(String pkgPattern) throws Exception {

		// build Java classes
		Map<String, UMLclass> classMap = this.getUmlModel().listClasses(pkgPattern);
		Iterator<String> cIt = classMap.keySet().iterator();
		while (cIt.hasNext()) {
			String key = cIt.next();
			UMLclass c = classMap.get(key);

			log.debug(c.getClassAddress().substring(2));
			pool.makeClass(c.getClassAddress().substring(2));

		}
	}

	public CtClass convertClassToJavaByteCode(UMLclass c) throws Exception {

		CtClass cc = pool.get(c.getClassAddress().substring(2));

		ClassFile ccFile = cc.getClassFile();
		ConstPool constpool = ccFile.getConstPool();
		// AnnotationsAttribute attr = new AnnotationsAttribute(constpool,
		// AnnotationsAttribute.visibleTag);
		// Annotation annot = new Annotation("javax.persistence.Entity",
		// constpool);
		// attr.addAnnotation(annot);
		// ccFile.addAttribute(attr);
		ccFile.setVersionToJava5();

		Iterator<UMLattribute> aIt = c.getAttributes().iterator();
		while (aIt.hasNext()) {
			UMLattribute a = aIt.next();

			if (!a.getToImplement()) {
				continue;
			}

			String fStr = this.generateAttrDeclaration(a);
			log.debug(fStr);
			CtField f = CtField.make(fStr, cc);
			cc.addField(f);

			CtMethod g = CtNewMethod.getter("get" + this.generateStemString(a), f);
			cc.addMethod(g);

			CtMethod s = CtNewMethod.setter("set" + this.generateStemString(a), f);
			cc.addMethod(s);
		}

		Iterator<UMLrole> rIt = c.getAssociateRoles().values().iterator();
		while (rIt.hasNext()) {
			UMLrole r = rIt.next();

			if (!r.getToImplement()) {
				continue;
			}

			String fStr = this.generateNonGenericsAttrDeclaration(r);
			log.debug(fStr);

			CtField f = CtField.make(fStr, cc);
			cc.addField(f);

			CtMethod g = CtNewMethod.getter("get" + this.generateStemString(r), f);
			cc.addMethod(g);

			CtMethod s = CtNewMethod.setter("set" + this.generateStemString(r), f);
			cc.addMethod(s);

		}

		return cc;

	}

	public String generateImportStatements(UMLclass c, String pkgPattern) {

		String code = "";

		String addr = c.getPkg().getPkgAddress();

		Pattern patt = Pattern.compile(pkgPattern);

		Set<String> repeatCheck = new HashSet<String>();

		Iterator<UMLattribute> aIt = c.getAttributes().iterator();
		while (aIt.hasNext()) {
			UMLattribute a = aIt.next();
			UMLclass impC = a.getType();

			if (impC.isDataType())
				continue;

			if (!a.getToImplement())
				continue;

			addr = impC.getClassAddress();
			addr = addr.substring(2, addr.length());

			Matcher m = patt.matcher(addr);
			if (!m.find()) {
				continue;
			}

			String stmt = "import " + addr + ";";
			if (!repeatCheck.contains(stmt)) {

				code += stmt + "\n";
				repeatCheck.add(stmt);
			}

		}

		Iterator<String> rIt = c.getAssociateRoles().keySet().iterator();
		while (rIt.hasNext()) {
			String key = rIt.next();
			UMLrole r = c.getAssociateRoles().get(key);

			if (!r.getToImplement())
				continue;

			if (r.getImplementz() == null)
				addr = r.getDirectClass().getClassAddress();
			else
				addr = r.getImplementz().getDirectClass().getClassAddress();

			addr = addr.substring(2, addr.length());

			Matcher m = patt.matcher(addr);
			if (!m.find()) {
				continue;
			}

			String stmt = "import " + addr + ";";
			if (!repeatCheck.contains(stmt)) {
				code += stmt + "\n";
				repeatCheck.add(stmt);
			}

		}

		if (c.getParent() != null) {

			addr = c.getParent().getClassAddress();
			addr = addr.substring(2, addr.length());

			String stmt = "import " + addr + ";";
			if (!repeatCheck.contains(stmt)) {
				code += stmt + "\n";
				repeatCheck.add(stmt);
			}

		}

		return code;
	}

	private String commentOut(String toBeCommented, int indent) {

		String out = "";
		String[] lines = toBeCommented.split("\\n");

		out += this.indent(indent) + "/**\n";

		for (int i = 0; i < lines.length; i++) {
			String l = lines[i];
			out += this.indent(indent) + " * " + l + "\n";
		}
		out += this.indent(indent) + "*/\n";

		return out;

	}

	private String indent(int indent) {

		String out = "";

		for (int j = 0; j < indent; j++) {
			out += "\t";
		}

		return out;

	}

	public void buildMavenProject(File srcJarFile, File jarFile, String group, String artifactId, String version,
			String bmkegParentVersion) throws Exception {

		UMLmodel m = this.getUmlModel();

		if (group == null || group.length() == 0) {
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
		pom += "	<artifactId>" + artifactId + "</artifactId>\n";
		pom += "	<version>" + version + "</version>\n";
		pom += "	<packaging>jar</packaging>\n";
		pom += "	<parent>\n";
		pom += "		<groupId>edu.isi.bmkeg</groupId>\n";
		pom += "		<artifactId>bmkeg-parent</artifactId>\n";
		pom += "		<version>" + bmkegParentVersion + "</version>\n";
		pom += "		<relativePath>../bmkeg-parent</relativePath>\n";
		pom += "	</parent>\n";
		pom += "	<properties>\n";
		pom += "		<junit.version>4.11</junit.version>\n";
		pom += "		<spring.version>4.0.0.RELEASE</spring.version>\n";
		pom += "	</properties>\n";
		pom += "	<build>\n";
		pom += "		<plugins>\n";
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
		pom += "	<dependencies>\n";
		pom += "		<dependency>\n";
		pom += "			<groupId>org.projectlombok</groupId>\n";
		pom += "			<artifactId>lombok</artifactId>\n";
		pom += "			<version>1.14.8</version>\n";
		// Older version works with Maven, newer version does not. 
		//pom += "			<version>1.16.6</version>\n";
		pom += "			<scope>provided</scope>\n";
		pom += "		</dependency>\n";
		pom += "		<dependency>\n";
		pom += "			<groupId>org.springframework.data</groupId>\n";
		pom += "			<artifactId>spring-data-elasticsearch</artifactId>\n";
		pom += "			<version>1.2.0.RELEASE</version>\n";
		pom += "		</dependency>\n";
		pom += "		<dependency>\n";
		pom += "			<groupId>org.springframework</groupId>\n";
		pom += "			<artifactId>spring-context</artifactId>\n";
		pom += "			<version>${spring.version}</version>\n";
		pom += "			<exclusions>\n";
		pom += "				<exclusion>\n";
		pom += "					<groupId>commons-logging</groupId>\n";
		pom += "					<artifactId>commons-logging</artifactId>\n";
		pom += "				</exclusion>\n";
		pom += "			</exclusions>\n";
		pom += "		</dependency>\n";
		pom += "		<dependency>\n";
		pom += "			<groupId>org.springframework</groupId>\n";
		pom += "			<artifactId>spring-test</artifactId>\n";
		pom += "			<version>${spring.version}</version>\n";
		pom += "			<scope>test</scope>\n";
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

		//
		// Write the model file to this temporary location
		//
		String suffix = ".tmp";
		if (m.getSourceType().equals(UMLmodel.XMI_MAGICDRAW))
			suffix = "_mgd.xml";
		else if (m.getSourceType().equals(UMLmodel.XMI_POSEIDON))
			suffix = "_pos.xml";

		File uml = new File(main_resources_vpdmf.getPath() + "/" + m.getName() + suffix);
		FileOutputStream fos = new FileOutputStream(uml);
		if (m.getSourceData() != null)
			fos.write(m.getSourceData());
		fos.close();
		filesInSrcJar.put("src/main/resources/model/" + uml.getName(), uml);
		
		Map<String, File> pojoFiles = this.javaBuilder.generateJavaCodeForModel(main_java, ".*", false);
		for(String key : pojoFiles.keySet() ) {
			filesInSrcJar.put("src/main/java/" + key, pojoFiles.get(key));
		}
		
		Map<String, File> esFiles = this.generateJavaCodeForModel(main_java, ".*", false);
		for(String key : esFiles.keySet() ) {
			filesInSrcJar.put("src/main/java/" + key, esFiles.get(key));
		}
		
		Converters.jarIt(filesInSrcJar, srcJarFile);

		if (jarFile == null)
			return;

		//
		// Use maven to compile the code
		//
		// MavenCli cli = new MavenCli();
		// int result = cli.doMain(new String[]{"compile"},
		// tempUnzippedDirectory.getPath(),
		// System.out, System.out);

		String out = LocalMavenInstall.runMavenCommand("package -f " + pomFile.getAbsolutePath());
		log.debug(out);

		File f1 = new File(dAddr + "/target/" + artifactId + "-" + version + ".jar");

		if (!f1.exists())
			throw new Exception("Build for " + f1.getName() + " failed. Check source: " + pomFile.getAbsolutePath());

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

	public Map<String, String> getQueryObjectLookupTable() {
		return queryObjectLookupTable;
	}

	public void setQueryObjectLookupTable(Map<String, String> queryObjectLookupTable) {
		this.queryObjectLookupTable = queryObjectLookupTable;
	}

	public Map<String, String> getJavaLookupTable() {
		return javaLookupTable;
	}

	public void setJavaLookupTable(Map<String, String> javaLookupTable) {
		this.javaLookupTable = javaLookupTable;
	}

	public boolean isBuildQuestions() {
		return buildQuestions;
	}

	public void setBuildQuestions(boolean buildQuestions) {
		this.buildQuestions = buildQuestions;
	}

	public Set<UMLclass> getClassesToIndex() {
		return classesToIndex;
	}

	public void setClassesToIndex(Set<UMLclass> classesToIndex) {
		this.classesToIndex = classesToIndex;
	}

}
