/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 * @author cmcgee
 */
public interface StatementHandler<P, R> {

    public String getSafeSQL();

    public void setParameters(P p, PreparedStatement pstmt) throws SQLException;

    public void result(R r, ResultSet rs) throws SQLException;

}
