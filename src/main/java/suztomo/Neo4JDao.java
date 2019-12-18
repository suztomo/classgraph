package suztomo;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.neo4j.driver.Values.parameters;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;

public class Neo4JDao {

  private final Driver driver;

  private Neo4JDao() {
    driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("", ""));
  }

  public static Neo4JDao create() {
    return new Neo4JDao();
  }

  void saveArtifactToClasses(String artifactCoordinates, List<String> classFiles) {
    List<Map<String, String>> properties =
        classFiles.stream()
            .map(f -> ImmutableMap.of("name", f, "artifact", artifactCoordinates))
            .collect(toImmutableList());
    Map<String, Object> parameters = ImmutableMap.of("javaClasses", properties);
    // https://github.com/neo4j/neo4j-java-driver/issues/443#issuecomment-351359527
    String query = "UNWIND $javaClasses AS javaClass " +
        "MERGE (a:Artifact{name: javaClass.artifact })\n" +
        "MERGE (j:JavaClass{name: javaClass.name, artifact: javaClass.artifact })\n" +
        "MERGE (a)-[:CONTAIN]->(j)";
    try (Session session = driver.session()) {
      session.writeTransaction(tx -> tx.run(query, parameters));
    }
  }

  void saveClassToMethods(String artifactCoordinates, JavaClass javaClass) {
    ImmutableList<ImmutableMap<String, String>> properties = Arrays.stream(javaClass.getMethods())
        .map(m -> ImmutableMap.of("name", m.getName(), "descriptor", m.getSignature()))
        .collect(toImmutableList());
    Map<String, Object> parameters = ImmutableMap.of("methods", properties,
        "artifact", artifactCoordinates,
        "className", javaClass.getClassName());

    String query = "UNWIND $methods AS method " +
        "MERGE (m:Method{name: method.name, descriptor: method.descriptor })\n" +
        "MERGE (j:JavaClass{name: $className, artifact: $artifact })\n" +
        "MERGE (j)-[:HAS]->(m)";
    try (Session session = driver.session()) {
      session.writeTransaction(tx -> tx.run(query, parameters));
    }
  }

  void saveClassToFields(String artifactCoordinates, JavaClass javaClass) {
    ImmutableList<ImmutableMap<String, String>> properties = Arrays.stream(javaClass.getFields())
        .map(m -> ImmutableMap.of("name", m.getName(), "descriptor", m.getSignature()))
        .collect(toImmutableList());
    Map<String, Object> parameters = ImmutableMap.of("fields", properties,
        "artifact", artifactCoordinates,
        "className", javaClass.getClassName());

    String query = "UNWIND $fields AS field " +
        "MERGE (f:Field{name: field.name, descriptor: field.descriptor })\n" +
        "MERGE (j:JavaClass{name: $className, artifact: $artifact })\n" +
        "MERGE (j)-[:HAS]->(f)";
    try (Session session = driver.session()) {
      session.writeTransaction(tx -> tx.run(query, parameters));
    }
  }
}
