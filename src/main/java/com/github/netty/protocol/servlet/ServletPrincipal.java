package com.github.netty.protocol.servlet;

import java.io.Serializable;
import java.security.Principal;

/**
 * The servlet identity
 * @author wangzihao
 */
public class ServletPrincipal implements Principal,Serializable {

    private final String name;
    private String password;

    public ServletPrincipal(String name,String password) {
        if (name == null) {
            throw new NullPointerException("null name is illegal");
        }
        this.name = name;
        this.password = password;
    }

    /**
     * Compares this principal to the specified object.
     *
     * @param object The object to compare this principal against.
     * @return true if they are equal; false otherwise.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object instanceof ServletPrincipal) {
            return name.equals(((ServletPrincipal)object).getName());
        }
        return false;
    }

    /**
     * Returns a hash code for this principal.
     *
     * @return The principal's hash code.
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Returns the name of this principal.
     *
     * @return The principal's name.
     */
    @Override
    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Returns a string representation of this principal.
     *
     * @return The principal's name.
     */
    @Override
    public String toString() {
        return name;
    }
}
