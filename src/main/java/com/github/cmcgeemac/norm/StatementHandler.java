/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Internal use only. Used for code generation and can change without notice.
 * External classes should neither implement this interface nor use it.
 */
public interface StatementHandler<P, R> {

    public String getSafeSQL();

    public void setParameters(P p, PreparedStatement pstmt, Connection conn) throws SQLException;

    public void result(R r, ResultSet rs) throws SQLException;

}
