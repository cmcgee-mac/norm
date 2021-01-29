/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.cmcgeemac.norm;


/**
 * Represents an iterable that also must be closed afterwards.
 */
public interface CloseableIterable<R> extends Iterable<R>, AutoCloseable {

}
