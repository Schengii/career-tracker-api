package de.schenk.careertracker.web;

import java.util.Set;
import java.util.TreeSet;

/**
 * Thrown when a client sorts by a property that is not part of the public API.
 */
public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String property, Set<String> allowed) {
        super("Cannot sort by '" + property + "'. Allowed: " + new TreeSet<>(allowed));
    }
}
