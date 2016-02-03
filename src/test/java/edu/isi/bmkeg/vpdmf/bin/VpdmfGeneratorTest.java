package edu.isi.bmkeg.vpdmf.bin;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import edu.isi.bmkeg.utils.springContext.BmkegProperties;
import junit.framework.TestCase;

public class VpdmfGeneratorTest extends TestCase {
	
	ApplicationContext ctx;

	BuildVpdmfMysqlMavenZip p;
	
	String login, password, dbUrl;

	File pomFile, srcDir, buildDir;
	
	@Before
	public void setUp() throws Exception {
		
		BmkegProperties prop = new BmkegProperties();
		
		login = prop.getDbUser();
		password = prop.getDbPassword();
		dbUrl = prop.getDbUrl();
		
		int l = dbUrl.lastIndexOf("/");
		if (l != -1)
			dbUrl = dbUrl.substring(l + 1, dbUrl.length());
	
		pomFile = ctx.getResource(
				"classpath:edu/isi/bmkeg/vpdmf/people/pom.xml"
				).getFile();
		srcDir = pomFile.getParentFile();
		buildDir = new File( srcDir.getPath() + "/target");
		
	}

	@After
	public void tearDown() throws Exception {
	//	Converters.recursivelyDeleteFiles(buildDir);
	}

	@Test
	public final void testBuildVpdmfMysqlMavenZip() throws Exception {
				
		String[] args = new String[] { 
				srcDir.getPath(),
				buildDir.getPath(),
				"1.1.5-SNAPSHOT"
				};
		
		BuildVpdmfMysqlMavenZip.main(args);
				
	}

	@Test
	public final void testBuildVpdmfModelMavenProject() throws Exception {
				
		String[] args = new String[] { 
				srcDir.getPath(),
				buildDir.getPath(),
				"1.1.5-SNAPSHOT"
				};
				
		BuildVpdmfModelMavenProject.main(args);
				
	}
	
	@Test
	public final void testBuildVpdmfServicesMavenProject() throws Exception {
				
		String[] args = new String[] { 
				srcDir.getPath(),
				buildDir.getPath(),
				"1.1.5-SNAPSHOT"
				};
				
		BuildVpdmfServicesMavenProject.main(args);
				
	}
	
}

