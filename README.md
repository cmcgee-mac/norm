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


## Simple private queries

Here is a light-weight example of a private query that you can put inline into
a method.

```java
    public void performQuery(int baz) throws Exception {
        class p implements NoP {
            int baz = baz;
        }

        class r implements NoR {
            int foo;
        }

        new @SQL("SELECT foo "
                  + "FROM bar WHERE "
                  + "bar.baz = :baz;")
        NormStatementt<p, r>() {
        }.executeQuery(dbConn)
         .forEach(r -> System.out.println(r.foo));
    }
```

You can see that the SQL query is provided as a Java annotation, which does not
permit any non-static content from entering the query, which could be the vector
for SQL injection. This is one of the built-in safety mechanisms. Substitution is
done only after the statement has been prepared by the database using named
tokens (e.g. ":baz") that match fields in the parameters class.

The statement's executeQuery() method returns an list of results that iterable
and streamable as needed from Java. There is no need for verbose and error-prone
reflection on the result set. The fields can be read directly from the result
objects.

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

Private statements like the one above are best used for simple queries that can
be more easily verified by code inspection and are unlikely to change very often.
For re-usable and more complex queries you can add them as statics into a class
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
  static class Statement extends NormStatements<StatementParams,StatementResult> {
  }

  // Let's save some of the costs of construction each time the statement is executed
  static final Statement STATEMENT = new Statement();

  public void performQuery() throws Exception {
    STATEMENT.executeQuery(dbConn, new ReqParams().setBaz(100))
      .forEach(r -> System.out.println(r.foo)); 
    }
  }
}
```

Once you declare your statement as first class (outside of a method) 
you get additional features such as compile-time checking. For example, if you have
variables in your SQL that don't match a field in the parameters class then you
get a compile error. If you have malformed SQL code the compiler will show an error
This is the recommended approach for more complex and dynamic statements. It's
better for a badly formed SQL code to fail at compile time than at runtime.

Also, since the statement is static and has package visibility it can be used
in an integration test environment with a database connection to drive different
test cases for the SQL statement directly.

```java
@Test
public void testMyStatement() {
    Connection conn = ...
    Outer.STATEMENT.execute(conn, new Outer.StatementParameter().setBaz(1))
      .forEach( r -> Assert.assertEquals(1, r.baz) );
}
```

Another benefit of first class statements is that they are much faster for
statements that produce a large number of results or are repetitive. This is due
to the code generation that becomes possible when the statements exist outside of
a method. Without the code generation all of the operations are done using Java
reflection, which can be slower. Those are the tradeoffs

## Statements without parameters

Statements don't always need parameters. Sometimes they are querying for all
rows in a small table or some static filter that is known to be constant. In
this case we use the built-in NoP (ie. No Parameters) placeholder.

```java
        new @SQL("SELECT foo "
                  + "FROM bar WHERE "
                  + "bar.baz = 123;")
        NormStatement<NoP, r>() {
        }.executeQuery(dbConn)).forEach ( r -> System.out.println(r.foo) );
```

## Statements without results

Some statements don't produce any results. They could be updates or insert or
even creating new tables. In these cases you can use the NoR (ie. No Results)
placeholder so that you don't have to provide an empty class yourself. Also,
there are other execute methods, such as executeUpdate() and execute() on the
NormStatement class that you can use to either return the number of rows updated
or just execute the query caring only if an exception is thrown.

```java
        int updates =
          new @SQL("UPDATE baz "
                  + "SET bar.foo = :foo "
                  + "WHERE bar.baz = :baz;")
          NormStatement<p, NoR>() {
          }.executeUpdate(dbConn, new r().setFoo(1).setBaz(2)));

        System.out.println("Updated " + updates + " rows");
```

## Force code generation

The code generation can sometimes be aborted if the conditions are not suitable
for it. This can go unnoticed and can slow down your statement execution. There
is a way to mark a statement as requiring code generation so that you can get
compile time errors or runtime errors if the code generation is not in place.

```java
        int updates =
          new @SQL("UPDATE baz "
                  + "SET bar.foo = :foo "
                  + "WHERE bar.baz = :baz;")
          @AssertCodeGen // COMPILE ERROR
          NormStatement<p, NoR>() {
          }.executeUpdate(dbConn, new r().setFoo(1).setBaz(2)));

        System.out.println("Updated " + updates + " rows");
```

In this example, you'll find that the code will not compile because the AssertCodeGen
annotation cannot be applied to the anonymouse inner class. The code generation can't
work in this case either.

```java
public class Outer {
  private static class StatementParams implements Parameters {
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
  @AssertCodeGen
  static class Statement extends NormStatements<StatementParams,StatementResult> { // COMPILE ERROR
  }
}
```

The code generation generates real, but slightly hidden Java classes to handle the
setting of parameters and putting the columns of the result set into objects from your
results Java class. It's able to do this within the scope of the package, but it cannot
work with private classes. The above case will fail to compile because the parameters
class is private and the assertion annotation is there. If you remove the assertion this
statement would be run with reflection, which is slower but able to work with private
visibility.

## The ORM Trap

It is possible to create public classes for the NORM statements, parameters
and results, but this is not recommended since it promotes the use of more
generalized SQL statements instead of case-specific ones that can be customized
easily without large refactoring operations on your code base. We try to tap into
the power of SQL directly for each situation. If you find yourself using many
shared statements and heavily reproducing the structure of your database in Java
then perhaps an ORM, such as JPA,  will be a better fit for your project.

## Troubleshooting

### Note about NetBeans

NetBeans has a [bug](https://issues.apache.org/jira/browse/NETBEANS-5331) that interferes
with updates to the generated code. A workaround is to disable the "compile on save" option
when using nb-javac in your project settings (right-click project -> Properties -> Build -> Compile).

