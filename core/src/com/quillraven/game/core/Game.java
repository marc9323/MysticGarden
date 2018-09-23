package com.quillraven.game.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.quillraven.game.core.gamestate.EGameState;
import com.quillraven.game.core.gamestate.GameState;
import com.quillraven.game.core.input.InputManager;
import com.quillraven.game.core.ui.HUD;

import java.util.EnumMap;
import java.util.Map;

public class Game implements Disposable {
    private static final String TAG = Game.class.getSimpleName();

    private final HUD hud;
    private final EnumMap<EGameState, GameState> gameStateCache;
    private GameState activeState;

    private float accumulator;

    public Game(final EGameState initialState) {
        gameStateCache = new EnumMap<>(EGameState.class);
        accumulator = 0;

        hud = new HUD();
        Gdx.input.setInputProcessor(new InputMultiplexer(InputManager.INSTANCE, hud.getStage()));

        setGameState(initialState, true);
    }

    public void setGameState(final EGameState gameStateType, final boolean disposeActive) {
        if (activeState != null) {
            Gdx.app.debug(TAG, "Deactivating gamestate " + (disposeActive ? "and disposing" : "") + " " + activeState.getType());
            activeState.deactivate();
            if (disposeActive) {
                gameStateCache.remove(activeState.getType());
                activeState.dispose();
            }
        }

        activeState = gameStateCache.computeIfAbsent(gameStateType, this::createGameState);
        Gdx.app.debug(TAG, "Activating gamestate " + activeState.getType());
        activeState.activate();
        activeState.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private GameState createGameState(final EGameState gameStateType) {
        Gdx.app.debug(TAG, "Creating new gamestate: " + gameStateType);

        try {
            final GameState newGS = (GameState) ClassReflection.getConstructor(gameStateType.getGameStateType(), EGameState.class, HUD.class).newInstance(gameStateType, hud);
            gameStateCache.put(gameStateType, newGS);
            return newGS;
        } catch (ReflectionException e) {
            throw new GdxRuntimeException("Could not create gamestate of type " + gameStateType, e);
        }
    }

    public void process() {
        final float deltaTime = Gdx.graphics.getRawDeltaTime();
        final float fixedTimeStep = 1 / 60.0f;
        accumulator += deltaTime > 0.25f ? 0.25f : deltaTime;

        while (accumulator >= fixedTimeStep) {
            hud.step(fixedTimeStep);
            activeState.step(fixedTimeStep);
            accumulator -= fixedTimeStep;
        }

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        activeState.render(accumulator / fixedTimeStep);
        hud.render();
    }

    public void resize(final int width, final int height) {
        activeState.resize(width, height);
        hud.resize(width, height);
    }

    @Override
    public void dispose() {
        for (final Map.Entry<EGameState, GameState> entry : gameStateCache.entrySet()) {
            Gdx.app.debug(TAG, "Disposing gamestate " + entry.getKey());
            entry.getValue().deactivate();
            entry.getValue().dispose();
        }
        hud.dispose();
    }
}
