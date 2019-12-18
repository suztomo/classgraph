package suztomo;

import static org.neo4j.driver.Values.parameters;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;

/**
 * Run Neo4J daemon via Docker:
 *
 * <pre>
 *   docker run     --publish=7474:7474 --publish=7687:7687     --volume=$HOME/neo4j/data:/data     --volume=$HOME/neo4j/logs:/logs     --user="$(id -u):$(id -g)"    --env NEO4J_AUTH=none  neo4j:3.5
 * </pre>
 */
public class HelloWorldExample implements AutoCloseable
{
  private final Driver driver;

  public HelloWorldExample( String uri, String user, String password )
  {
    driver = GraphDatabase.driver( uri, AuthTokens.basic( user, password ) );
  }

  @Override
  public void close() throws Exception
  {
    driver.close();
  }

  public void printGreeting( final String message )
  {
    try ( Session session = driver.session() )
    {
      String greeting = session.writeTransaction( new TransactionWork<String>()
      {
        @Override
        public String execute( Transaction tx )
        {
          Result result = tx.run("CREATE (a:Greeting) " +
                  "SET a.message = $message " +
                  "RETURN a.message + ', from node ' + id(a)",
              parameters("message", message));
          return result.single().get( 0 ).asString();
        }
      } );
      System.out.println( greeting );
    }
  }

  public static void main( String... args ) throws Exception
  {
    try ( HelloWorldExample greeter = new HelloWorldExample( "bolt://localhost:7687", "", "" ) )
    {
      greeter.printGreeting( "hello, world" );
    }
  }
}