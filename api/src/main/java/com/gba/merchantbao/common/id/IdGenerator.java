package com.gba.merchantbao.common.id;

/** Application-assigned IDs for new domain records; does not change legacy auto-increment IDs. */
@FunctionalInterface
public interface IdGenerator {
    long nextId();
}
