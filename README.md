No ORM
======

![Java CI with Maven](https://github.com/cmcgee-mac/norm/workflows/Java%20CI%20with%20Maven/badge.svg)

The No ORM (NORM) package is a set of Java utilities built on top of JDBC
to better integrate SQL queries into your Java code in a safer and type safe
manner, without re-describing SQL table structures in Java. Instead, the idea
is to enable the programmer to write their SQL queries and model the parameters
and results tailored to each query while plugging into Java's type system.

Guiding principles:
* SQL queries are specified in a static manner where no user data can contaminate them (ie. Java Annotations)
* Both parameters and results are (un)marshaled using Java classes with named and typed fields instead of verbose and error-prone reflective interfaces
* Prefer compile time and then initialization time checking over execution time checking to find problems earlier
* Choose flexible Java interfaces to enable flexible and safe uses of the queries

Here is a light-weight example of a private query that you can put inline into
a method.

```java
    public void performQuery(int baz) throws Exception {
        class p implements Parameters {
            int baz = baz;
        }

        class r implements Result {
            int foo;
        }

        try (CloseableIterable<r> rs
                = new @SQL("SELECT foo "
                        + "FROM bar WHERE "
                        + "bar.baz = :baz;") NormStatementWithResult<p, r>() {
                }.execute(dbConn)) {
            rs.forEach(r -> System.out.println(r.foo));
        }
    }
```

You can see that the SQL query is provided as a Java annotation, which does not
permit any non-static content from entering the query, which could be the vector
for SQL injection. This is one of the built-in safety mechanisms. Substitution is
done only after the statement has been prepared by the database using named
tokens (e.g. ":baz") that match fields in the parameters class.

The statement's execute() method returns an Iterable so that a variety of Java
constructs so that you could use forEach() with a lambda function or even convert
it to a Java Stream. Be sure to close the Iterable when you are finished
with it, or encapsulate it in a try-with-resources block.

```java
rs.forEach( r -> System.out.println(r.foo) );

Integer foo = StreamSupport.stream(rs
        .spliterator(), false)
        .map(r-> r.foo)
        .findFirst()
        .get();
```

At execution time the SQL variables are set using the parameters object. The default
constructor above will set the value to the input of the performQuery() method.
Substitution is done using the name of the field and its declared Java type.

Any misspelled variable names or types between your code and the result/parameter
class will produce runtime exceptions. If there are parameters that exist
in the query, but are not available in the parameters class they will discovered
as runtime exceptions.

The result class captures the expected result set columns and types so that they can
be more easily encapsulated as Java objects without the necessity for JDBC's
reflective interfaces. This tends to produce less verbose and more readable code.
Also, if you change the query and results then it's much easier to refactor code
that depends on a particular column or type in the output.

## First Class Statements

Inline statements like the one above are best used for simple queries that can
be more easily verified by code inspection and are unlikely to change very often.
For re-usable and more complex queries you can put them statically into a class
 as in the following example.

```java
public class Outer {
  static class StatementParams implements Parameters {
    int baz;

    public StatementParams setBaz(int newBaz) {
      baz = newBaz;
      return this;
    }
  }

  static class StatementResult implements Result {
    int foo;
  }

  @SQL("SELECT foo FROM bar WHERE bar.baz = :baz")
  static class Statement extends NormStatementWithResults<StatementParams,StatementResult> {
  }

  // Let's save some of the costs of construction each time the statement is executed
  static final Statement STATEMENT = new Statement();

  public void performQuery() throws Exception {
    try (CloseableIterable<StatementResult> rs = STATEMENT.execute(dbConn, new ReqParams().setBaz(100)) {
      for (StatementResult r: rs) {
        System.out.println(r.foo);
      }
    }
  }
}
```

Once you declare your statement as first class you get additional features
such as compile-time checking. For example, if you have
variables in your SQL that don't match a field in the parameters class then you
get a compile error. If you have malformed SQL code the compiler will show an error
This is the recommended approach for more complex and dynamic statements.

Also, since the statement is static and has package visibility it can be used
in an automated test environment with a database connection to drive different
test cases for the SQL query directly.

```
@Test
public void testMyStatement() {
    Connection conn = ...
    try (CloseableIterable<Outer.StatementResult> rs = Outer.STATEMENT.execute(conn, new Outer.StatementParameter().setBaz(1)) {
        rs.forEach( r -> Assert.assertEquals(1, r.baz) );
    }
}
```

Another benefit to first classs statements is that they are much faster for
statements that produce a large number of results or are repetitive. This is due
to the code generation that becomes possible when the statements exist outside of
a method. Without the code generation all of the operations are done using Java
reflection, which can be slower.

## The ORM Trap

It is also possible to create public classes for the NORM statements, parameters
and results, but this is not recommended since it promotes the use of more
generalized SQL statements instead of case-specific ones that can be customized
easily without large refactoring operations on your code base. We try to tap into
the power of SQL directly for each situation.

## Troubleshooting

### Note about NetBeans

NetBeans has a [bug](https://issues.apache.org/jira/browse/NETBEANS-5331) that interferes
with updates to the generated code. A workaround is to disable the "compile on save" option
when using nb-javac in your project settings (right-click project -> Properties -> Build -> Compile).

