package com.ykx.backend.agent;

import java.util.HashSet;
import java.util.Set;

public class AgentToolCallContext {

    private static final ThreadLocal<State> LOCAL = new ThreadLocal<>();

    public static void init(String userId, String sessionId) {
        State state = new State();
        state.userId = userId;
        state.sessionId = sessionId;
        LOCAL.set(state);
    }

    public static State get() {
        return LOCAL.get();
    }

    public static void clear() {
        LOCAL.remove();
    }

    public static class State {
        private String userId;
        private String sessionId;
        private int totalToolCalls;
        private int knowledgeSearchCalls;
        private final Set<String> calledKnowledgeQueries = new HashSet<>();

        public String getUserId() {
            return userId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public int getTotalToolCalls() {
            return totalToolCalls;
        }

        public void increaseTotalToolCalls() {
            this.totalToolCalls++;
        }

        public int getKnowledgeSearchCalls() {
            return knowledgeSearchCalls;
        }

        public void increaseKnowledgeSearchCalls() {
            this.knowledgeSearchCalls++;
        }

        public Set<String> getCalledKnowledgeQueries() {
            return calledKnowledgeQueries;
        }
    }
}