package client.stage;

public interface Stage {
    /**
     * executes the current stage and returns the next one.
     *
     * @return the stage that will be executed next.
     */
    Stage execute();
}
