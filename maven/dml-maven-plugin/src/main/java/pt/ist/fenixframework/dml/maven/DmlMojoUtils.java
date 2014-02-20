package pt.ist.fenixframework.dml.maven;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import pt.ist.fenixframework.core.DmlFile;
import pt.ist.fenixframework.core.Project;
import pt.ist.fenixframework.core.exception.ProjectException;

public class DmlMojoUtils {

    public static Project getProject(MavenProject project, List<File> dmlFiles, boolean isTest) throws IOException,
            ProjectException, MalformedURLException {
        List<Project> dependencies = new ArrayList<Project>();

        for (Artifact artifact : project.getDependencyArtifacts()) {
            if (artifact.getFile() == null || (artifact.getScope().equals("test") && !isTest)) {
                continue;
            }
            String absolutePath = artifact.getFile().getAbsolutePath();

            boolean hasProjectProperties = false;
            if (artifact.getFile().isDirectory()) {
                hasProjectProperties = new File(absolutePath + "/" + artifact.getArtifactId() + "/project.properties").exists();
            } else {
                JarFile jarFile = new JarFile(absolutePath);
                hasProjectProperties = jarFile.getJarEntry(artifact.getArtifactId() + "/project.properties") != null;
                jarFile.close();
            }
            if (hasProjectProperties) {
                dependencies.add(Project.fromName(artifact.getArtifactId()));
            }
        }

        List<DmlFile> dmls = new ArrayList<DmlFile>();
        for (File dml : dmlFiles) {
            dmls.add(new DmlFile(dml.toURI().toURL(), dml.getName()));
        }
        return new Project(project.getArtifactId(), project.getVersion(), dmls, dependencies, true);
    }

    public static URLClassLoader augmentClassLoader(Log log, Collection<String> classpathElements) {
        URL[] classesURL = new URL[classpathElements.size()];
        int i = 0;

        for (String path : classpathElements) {
            try {
                classesURL[i++] = new File(path).toURI().toURL();
            } catch (MalformedURLException e) {
                log.error(e);
            }
        }

        URLClassLoader loader = new URLClassLoader(classesURL, Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
        return loader;
    }

}
