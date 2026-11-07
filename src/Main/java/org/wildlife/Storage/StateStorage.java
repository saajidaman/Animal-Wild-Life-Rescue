package org.wildlife.Storage;

import java.io.IOException;
import org.wildlife.Model.State;
/** Persistence abstraction permits deterministic failure tests and alternative stores. */
public interface StateStorage {
    State load() throws IOException;
    void save(State state) throws IOException;
}
