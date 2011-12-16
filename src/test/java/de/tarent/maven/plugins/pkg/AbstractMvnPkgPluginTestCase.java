package de.tarent.maven.plugins.pkg;


import java.io.File;

import java.io.BufferedReader;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.model.License;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.runner.RunWith;

import de.tarent.maven.plugins.pkg.Packaging;
import de.tarent.maven.plugins.pkg.testingstubs.PkgArtifactStub;
import de.tarent.maven.plugins.pkg.testingstubs.PkgProjectStub;

@RunWith(JUnit4ClassRunner.class)
public abstract class AbstractMvnPkgPluginTestCase extends AbstractMojoTestCase {

	Packaging packagingPlugin;
	Upload packagingTransportPlugin;
	protected static final File TARGETDIR = new File(getBasedir()+ "/src/test/resources/dummyproject/target/");

	
	/**
	 * This is the key fingerprint for Test User <no@address.com></br>
	 * It is needed for test purposes
	 */
	static final String keyFingerprint = "FE68C8E434E475CC516472D67DB93B6461E99DD5";
	/**
	 * This is the keyID for Test User <no@address.com></br>
	 * It is needed for test purposes
	 */
	static final String keyID = "7db93b6461e99dd5";
	
	/**{@inheritDoc} */
	protected void setUp() throws Exception{
		super.setUp();
		FileUtils.forceMkdir(TARGETDIR);
		FileUtils.cleanDirectory(TARGETDIR);
		
	}

	/**{@inheritDoc} */	
	protected void tearDown()throws Exception{
		super.tearDown();
		FileUtils.cleanDirectory(TARGETDIR);
	}
	
	
	/**
	 * This method mocks the packaging environment. It loads an external pom, initialites the mvn-pkg-plugin
	 * and sets enough information for basic tests to succeed. It can then be manipulated to achieve more complex
	 * testing.
	 *  
	 * @param pom An external pom file containing at least the plugin section refferring to mvn-pkg-plugin. The 
	 * file should be tored under src/test/resources/dummyproject/
	 * @return
	 * @throws Exception
	 */
	public AbstractPackagingMojo mockEnvironment(String pomFilename, String goal) throws Exception{
		

        File pom = getTestFile( getBasedir(), "src/test/resources/dummyproject/" + pomFilename );
		// Create plugin based on the external pom file
        AbstractPackagingMojo packagingPlugin = (AbstractPackagingMojo) lookupMojo(goal, pom);
        packagingPlugin.setPluginContext(new HashMap<String,String>());       
        
        // Create a project contained by the plugin based on the external pom file 
        packagingPlugin.project = new PkgProjectStub(pom);

        // Parameters that are not part of the mvn-pkg-plugin section are somehow loaded into the project
        // TODO: Find why this problem exists and/or a more elegant way to do this
        
        packagingPlugin.project.setPackaging("jar");
        packagingPlugin.project.setName("DummyProject");
        packagingPlugin.project.setArtifactId("DummyProject");
        packagingPlugin.project.setDescription("DummyDescription");
        packagingPlugin.project.setUrl("http://DummyURL.com");
        packagingPlugin.project.setVersion("1.0.0");
        packagingPlugin.project.setLicenses(createLicenseList("License 1","License 2"));
        packagingPlugin.version =    packagingPlugin.project.getVersion();
        packagingPlugin.artifactId = packagingPlugin.project.getArtifactId();
        packagingPlugin.finalName =	 packagingPlugin.project.getArtifactId();
        
        packagingPlugin.buildDir =  TARGETDIR;
        packagingPlugin.outputDirectory = TARGETDIR;
        
        // Workaround for a bug (maven does not load default-value parameters:
        // http://maven.40175.n5.nabble.com/default-value-are-not-injected-td3907553.html
        
        packagingPlugin.ignorePackagingTypes = "pom";
        
        //Create artifact stub, as we wont actually compile anything 
		File f = new File(TARGETDIR +"/"+
				 packagingPlugin.finalName +  "." + 
				 packagingPlugin.project.getPackaging());
		f.createNewFile();
		PkgArtifactStub artifactStub = new PkgArtifactStub(f);	
        packagingPlugin.project.setArtifact(artifactStub);
        
        return packagingPlugin;
		
	}
	
	
	public File[] returnFilesFoundBasedOnSuffix(String suffix){
		
		final Pattern p = Pattern.compile(".*\\." + suffix);
	    return TARGETDIR.listFiles(new FileFilter() {			
			@Override
	        public boolean accept(File file) {
	            return p.matcher(file.getName()).matches();
			}
		});		
	}

	protected boolean numberOfRPMsIs(int i) {
		return returnFilesFoundBasedOnSuffix("rpm").length==i;
	}

	protected boolean numberOfDEBsIs(int i) {
		return returnFilesFoundBasedOnSuffix("deb").length==i;
	}
	
	private boolean debContains(Pattern p, String debArgs) throws MojoExecutionException{
		boolean result = false;
		String out = new String(); 
		try{
			out = IOUtils.toString(Utils.exec(new String[]{"dpkg",debArgs,
				returnFilesFoundBasedOnSuffix("deb")[0].getAbsolutePath()},TARGETDIR,
				"Failure checking contents", "Failure opening rpm file"));
		}catch (IOException e) {
			throw new MojoExecutionException(e.getMessage(),e);
		}
		//Log l = packagingPlugin.getLog();
		//l.info("Matching" + out + "/// to "+p);
		if (p.matcher(out).find()){
			result = true;
		}
		return result;
	}
	
	private boolean rpmContains(Pattern p, String rpmArgs) throws MojoExecutionException{
		boolean result = false;
		String out = new String();
		try{
			out = IOUtils.toString(Utils.exec(new String[]{"rpm","-pq",rpmArgs,
				returnFilesFoundBasedOnSuffix("rpm")[0].getAbsolutePath()},TARGETDIR,
				"Failure checking contents", "Failure opening rpm file"));
		}catch (IOException e) {
			throw new MojoExecutionException(e.getMessage(),e);
		}
		
		if (p.matcher(out).find()){
			result = true;
		}		
		return result;
	}
	
	protected boolean debContainsMainArtifact() throws MojoExecutionException, IOException {
		final Pattern p = Pattern.compile(".*"+
										  Pattern.quote(packagingPlugin.project.getArtifact().getFile().getName())+
										  ".*");
		return debContains(p, "-c");		
	}
	
	protected boolean rpmContainsMainArtifact() throws MojoExecutionException, IOException {
		final Pattern p = Pattern.compile(".*"+
				  Pattern.quote(packagingPlugin.project.getArtifact().getFile().getName())+
				  ".*");
		return rpmContains(p,"--dump");
	}
	
	protected boolean rpmIsSigned() throws MojoExecutionException, IOException {
		final Pattern p = Pattern.compile(keyID);
		return rpmContains(p,"-i");
	}
	
	protected boolean rpmContainsArtifact(String s) throws MojoExecutionException, IOException {
		final Pattern p = Pattern.compile(".*"+Pattern.quote(s)+".*");
		return rpmContains(p,"--dump");
	}
	
	protected boolean debContainsArtifact(String s) throws MojoExecutionException, IOException {
		final Pattern p = Pattern.compile(".*"+ Pattern.quote(s)+ ".*");
		return debContains(p, "-c");		
	}
		
	protected boolean rpmDependsOn(String s) throws MojoExecutionException, IOException {
		final Pattern p = Pattern.compile(Pattern.quote(s)+".*");
		return rpmContains(p,"-R");
	}	

	protected boolean debDependsOn(String s) throws MojoExecutionException, IOException {
		final Pattern p = Pattern.compile("Depends:.*"+Pattern.quote(s)+".*");
		return debContains(p, "--info");
	}
	
	protected List<License> createLicenseList(String ... strings)
	{
		List<License> licenses = new ArrayList<License>();
		for(int i=0;i<strings.length;i++){
			License l = new License();
			l.setName(strings[i]);
			l.setUrl("http://www.gnu.org/licenses/lgpl.txt");
			licenses.add(l);			
		}		
		return licenses;
	}

	protected boolean debContainsCopyrightFile() throws MojoExecutionException, IOException {
		final Pattern p = Pattern.compile("lines.*copyright");
		return debContains(p, "--info");
	}
	protected boolean debIsSigned() throws MojoExecutionException{
		Utils.exec(new String[]{"ar","x",returnFilesFoundBasedOnSuffix("deb")[0].getAbsolutePath()},
				   TARGETDIR,
			  	   "Error extracting package",
			  	   "Error extracting package");

		  File f = new File(TARGETDIR,"_pgporigin");
		  if(f.exists()){
			  return true;
		  }else{
			  return false;
		  }
	}

	
	protected boolean filecontains(File file, String lookup) throws IOException {
		FileInputStream fis = new FileInputStream(file);

		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(fis));
			try{
				String currentLine = "";
				while ((currentLine = in.readLine()) != null) {
					if (currentLine.indexOf(lookup) != -1)
						return true;
				}
			}finally{
				IOUtils.closeQuietly(in);
			}
		} finally {
			IOUtils.closeQuietly(fis);		
		}
		return false;
	}
	

}
