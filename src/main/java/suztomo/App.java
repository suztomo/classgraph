package suztomo;


import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.LruCacheClassPathRepository;
import org.apache.bcel.util.Repository;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

/**
 * Tool to import Java class and artifact into Neo4J.
 */
public class App {

  public static void main(String[] args) throws Exception {
    Neo4JDao dao = Neo4JDao.create();

    ImmutableList.Builder<String> builder = ImmutableList.builder();

    for (String arg : args) {
      builder.addAll(findCoordinatesFromRange(arg));
    }

    int count = 0;
    ImmutableList<String> coordinatesFromRange = builder.build();
    for (String coordinates : coordinatesFromRange) {
      ImmutableList<Path> paths = ClassPathBuilder
          .artifactsToClasspath(ImmutableList.of(new DefaultArtifact(coordinates)));
      importArtifact(dao, coordinates, paths.get(0));
      System.out
          .println(
              "Finished importing " + coordinates + "(" + (++count) + "/" + coordinatesFromRange
                  .size() + ")");
    }
  }

  private static ImmutableList<String> findCoordinatesFromRange(String versionRangeCoordinates) {

    String[] elements = versionRangeCoordinates.split(":", 3);
    String groupId = elements[0];
    String artifactId = elements[1];
    String versionRange = elements[2];

    RepositorySystem repositorySystem = RepositoryUtility.newRepositorySystem();
    RepositorySystemSession session = RepositoryUtility
        .newSession(repositorySystem);

    Artifact artifactWithVersionRange = new DefaultArtifact(groupId, artifactId, null,
        versionRange);
    VersionRangeRequest request =
        new VersionRangeRequest(
            artifactWithVersionRange, ImmutableList.of(RepositoryUtility.CENTRAL), null);

    try {
      VersionRangeResult result = repositorySystem
          .resolveVersionRange(session, request);

      List<Version> versions = result.getVersions();
      return versions.stream().map(v -> String.format("%s:%s:%s", groupId, artifactId, v))
          .collect(toImmutableList());
    } catch (VersionRangeResolutionException ex) {
      throw new RuntimeException("Failed to resolve version range", ex);
    }
  }

  private static void importArtifact(Neo4JDao dao, String artifactCoordinates, Path path)
      throws Exception {
    Repository repository = new LruCacheClassPathRepository(
        new ClassPath(path.toAbsolutePath().toString()), 500);

    URL jarUrl = path.toUri().toURL();
    URLClassLoader classLoaderFromJar = new URLClassLoader(new URL[]{jarUrl}, null);

    // Leveraging Google Guava reflection as BCEL doesn't list classes in a jar file
    com.google.common.reflect.ClassPath classPath =
        com.google.common.reflect.ClassPath.from(classLoaderFromJar);

    ImmutableList.Builder<String> classNames = ImmutableList.builder();
    for (ClassInfo classInfo : classPath.getAllClasses()) {
      try {
        JavaClass javaClass = repository.loadClass(classInfo.getName());
        classNames.add(javaClass.getClassName());
        dao.saveClassToMethods(artifactCoordinates, javaClass);
        dao.saveClassToFields(artifactCoordinates, javaClass);
      } catch (ClassNotFoundException ex) {
        System.out.println("Not found: " + ex.getMessage());
        continue;
      }
    }
    dao.saveArtifactToClasses(artifactCoordinates, classNames.build());
  }
}
