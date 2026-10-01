package com.knowledgegraph.assistant.host;

import org.springframework.jdbc.datasource.AbstractDataSource;
import java.sql.*;
import java.lang.reflect.*;
import java.time.Instant;
import java.util.regex.*;

/** Additional local guards; SELECT privileges still require independently reviewed evidence. */
final class AssistantReadDataSource extends AbstractDataSource {
    private final AssistantReadWiring.VerifiedReader identity;
    private final java.util.concurrent.atomic.AtomicBoolean authorityValid=new java.util.concurrent.atomic.AtomicBoolean(true);
    AssistantReadDataSource(AssistantReadWiring.VerifiedReader identity){this.identity=identity;}
    boolean authorityValid(){return authorityValid.get();}
    @Override public Connection getConnection() throws SQLException {
        if(!authorityValid() || !identity.valid(identity.grant(),Instant.now()))throw new SQLException("Reader authority unavailable");
        Connection raw=identity.assistantReadDataSource().getConnection();
        try {
            String user=raw.getMetaData().getUserName();
            if(user==null || !user.split("@",2)[0].equals(identity.account()))throw new SQLException("Reader identity mismatch");
            raw.setReadOnly(true);
            return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)-> {
                String name=method.getName();
                if(name.equals("unwrap") || name.equals("prepareCall") || name.equals("createStatement")
                        || name.equals("setReadOnly") && Boolean.FALSE.equals(args[0]))throw new SQLException("Read connection operation rejected");
                if(name.equals("isWrapperFor"))return false;
                if(name.equals("prepareStatement")) {
                    sql((String)args[0]);PreparedStatement statement=(PreparedStatement)invoke(raw,method,args);
                    statement.setQueryTimeout(3);
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)-> {
                        String operation=m.getName();
                        if(operation.equals("unwrap") || operation.equals("addBatch") || operation.startsWith("executeUpdate")
                                || operation.startsWith("executeLargeUpdate") || operation.equals("executeBatch") || operation.equals("executeLargeBatch")
                                || operation.startsWith("execute") && a!=null && a.length>0 && a[0] instanceof String)
                            throw new SQLException("Read statement operation rejected");
                        if(operation.equals("isWrapperFor"))return false;
                        if(operation.equals("getConnection"))return proxy;
                        return invoke(statement,m,a);
                    });
                }
                return invoke(raw,method,args);
            });
        } catch(Exception e) {authorityValid.set(false);raw.close();throw new SQLException("Reader identity unavailable");}
    }
    private void sql(String sql) throws SQLException {
        if(sql==null || !sql.stripLeading().toUpperCase(java.util.Locale.ROOT).matches("(?s)^SELECT\\s+.*")
                || Pattern.compile("(?i)(;|--|/\\*|\\b(into|outfile|infile|sleep|benchmark|update|insert|delete|alter|drop|create|grant|revoke|lock)\\b)").matcher(sql).find())
            throw new SQLException("Only approved SELECT statements are permitted");
        Matcher tables=Pattern.compile("(?i)\\b(?:from|join)\\s+([a-z_]+)").matcher(sql);boolean found=false;
        while(tables.find()) {
            found=true;if(!identity.selectTables().contains(tables.group(1).toLowerCase(java.util.Locale.ROOT)))
                throw new SQLException("Read table outside reviewed grant");
        }
        if(!found)throw new SQLException("Read table required");
    }
    private static Object invoke(Object target,Method method,Object[] args) throws Throwable {
        try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}
    }
    @Override public Connection getConnection(String user,String password)throws SQLException {
        throw new SQLException("Explicit reader only; alternate credentials rejected");
    }
}
