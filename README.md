No ORM
======

The No ORM (NORM) package is a set of Java utilities built on top of JDBC
to better integrate SQL queries into your Java code in a safer and type safe
manner, without re-describing SQL table structures in Java. Instead, the idea
is to enabling the programmer to write their SQL queries and model the parameters
and results tailed to each query plugging into Java's type system.

Guiding principles:
* SQL queries are specified in a static manner where no user data can contaminate them (ie. Java Annotations)
* Both parameters and results are (un)marshaled using Java classes with named and typed fields instead of verbose and error-prone reflective interfaces
* Prefer compile time and then initialization time checking over execution time checking to find problems earlier
* Choose flexible Java interfaces to enable flexible and safe uses of the queries

Here is a light-weight example of a private query that you can put directly into
a method.

```
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
for SQL injection. This one of the built-in safety mechanisms. Substitution is
done only after the statement has been prepared by the database using named
tokens (e.g. ":baz") that match fields in the parameters class.

At execution time the SQL variables are set using parameters object. The default
constructor above will set the value to the input of the performQuery() method.
Substitution is done using the name of the field and its declared Java type.

Any misspelled variable names or types between your code and the result/parameter
class will produce immediate compile errors. If there are parameters that exist
in the query, but are not available in the parameters class they will discovered
as runtime exceptions.

The result class captures the expected result set columns and types so that they can
be more easily encapsulated as Java objects without the necessity for JDBC's
reflective interfaces. This tends to produce less verbose and more readable code.
Also, if you change the query and results then it's much easier to refactor code
that depends on a particular column or type in the output.

For re-usable queries you can put them statically into a class as in the following
example.

```
public class Outer {
  static class ReqParams implements Parameters {
    int baz;

    public ReqParams setBaz(int newBaz) {
      baz = newBaz;
      return this;
    }
  }

  static class ReqResult implements Result {
    int foo;
  }

  static NormStatementWithResults<ReqParams,ReqResult> REQUEST =
    new @SQL("SELECT foo FROM bar WHERE bar.baz = :baz;")
    NormStatementWithResults<ReqParams,ReqResult>() {}

  public void performQuery() throws Exception {
    try (CloseableIterable<ReqRsult> rs = REQUEST.execute(dbConn, new ReqParams().setBaz(100)) {
      for (ReqResult r: rs) {
        System.out.println(r.foo);
      }
    }
  }
}
```

In this case fields in the parameters class that do not exist as variables in the
SQL statement or vice-versa will fail as soon as the class is loaded, which is
often much sooner than when the first time a query is executed.

It is also possible to create public classes for the NORM statements, parameters
and results, but this is not recommended since it promotes the use of more
generalized SQL statements instead of case-specific ones that can be customized
easily without large refactoring operations on your code base. We try to tap into
the power of SQL directly for each situation.

The statement's execute() method returns an Iterable so that a variety of Java
constructs so that you could use forEach() with a lambda function or even convert
it to a Java Stream. Be sure to close the Iterable when you are finished
with it, or encapsulate it in a try-with-resources block.

```
rs.forEach( r -> System.out.println(r.foo) );

Integer foo = StreamSupport.stream(rs
        .spliterator(), false)
        .map(r-> r.foo)
        .findFirst()
        .get();
```
