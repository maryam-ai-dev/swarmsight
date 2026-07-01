"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";

export interface AgentView {
  id: string;
  name: string;
  version: string;
  endpointUrl: string;
  environment: string;
  requestedActions: string[];
  ownerEmail: string | null;
  active: boolean;
  createdAt: string;
  workflow: string;
}

const DEFAULT_AGENT = "housing-appeals-agent-v3";

interface AgentContextValue {
  agents: AgentView[];
  agentId: string;
  setAgentId: (id: string) => void;
  selected: AgentView | null;
  reload: () => void;
  loading: boolean;
}

const AgentContext = createContext<AgentContextValue>({
  agents: [],
  agentId: DEFAULT_AGENT,
  setAgentId: () => {},
  selected: null,
  reload: () => {},
  loading: true,
});

export function useAgent() {
  return useContext(AgentContext);
}

// Holds the registered agents and which one the assurance screens act on. The
// selection defaults to the demo housing agent, or the first registered agent.
export function AgentProvider({ children }: { children: React.ReactNode }) {
  const [agents, setAgents] = useState<AgentView[]>([]);
  const [agentId, setAgentId] = useState<string>(DEFAULT_AGENT);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(() => {
    fetch("/_authority/agents")
      .then((r) => (r.ok ? r.json() : []))
      .then((a: AgentView[]) => {
        setAgents(a);
        setAgentId((prev) =>
          a.find((x) => x.id === prev) ? prev : a[0]?.id || prev,
        );
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const selected = agents.find((a) => a.id === agentId) || null;

  return (
    <AgentContext.Provider
      value={{ agents, agentId, setAgentId, selected, reload, loading }}
    >
      {children}
    </AgentContext.Provider>
  );
}
