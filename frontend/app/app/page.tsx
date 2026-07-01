"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { NavContext, ScreenId } from "@/lib/nav";
import { UserProvider } from "@/lib/user";
import { AgentProvider } from "@/lib/agent";
import { CaseProvider } from "@/lib/caseref";
import { AccountChip } from "@/components/AccountChip";
import { StoryMode } from "@/components/StoryMode";
import { Landing } from "@/components/screens/Landing";
import { Home } from "@/components/screens/Home";
import { Status } from "@/components/screens/Status";
import { Departments } from "@/components/screens/Departments";
import { Department } from "@/components/screens/Department";
import { Agents } from "@/components/screens/Agents";
import { CheckAgent } from "@/components/screens/CheckAgent";
import { TestResults } from "@/components/screens/TestResults";
import { CertificateScreen } from "@/components/screens/Certificate";
import { Gate } from "@/components/screens/Gate";
import { Sources } from "@/components/screens/Sources";
import { Officer } from "@/components/screens/Officer";
import { CaseScreen } from "@/components/screens/Case";
import { Masking } from "@/components/screens/Masking";
import { Policies } from "@/components/screens/Policies";
import { ProofPack } from "@/components/screens/ProofPack";
import { Oversight } from "@/components/screens/Oversight";
import { AgentLog } from "@/components/screens/AgentLog";

const SCREEN_IDS: ScreenId[] = [
  "landing", "home", "status", "departments", "department", "agents", "check", "results",
  "certificate", "gate", "sources", "officer", "case", "masking", "policies",
  "audit", "oversight", "agentlog",
];

function AppShell() {
  const params = useSearchParams();
  // Deep-link target from the public landing's CTAs (e.g. /app?screen=check).
  const requested = params.get("screen") as ScreenId | null;
  const initial: ScreenId =
    requested && SCREEN_IDS.includes(requested) ? requested : "home";

  const [active, setActive] = useState<ScreenId>(initial);
  const [story, setStory] = useState(false);
  const frameRef = useRef<HTMLDivElement>(null);
  const firstRender = useRef(true);

  function go(id: ScreenId) {
    setActive(id);
  }

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    frameRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [active]);

  return (
    <NavContext.Provider value={{ active, go }}>
      <UserProvider>
        <AgentProvider>
          <CaseProvider>
          <AccountChip onStory={() => setStory(true)} />
          <StoryMode open={story} onClose={() => setStory(false)} />
          <div
            className={"app-canvas" + (story ? " story-open" : "")}
            ref={frameRef}
          >
            <Landing active={active} />
            <Home active={active} onStory={() => setStory(true)} />
            <Status active={active} />
            <Departments active={active} />
            <Department active={active} />
            <Agents active={active} />
            <CheckAgent active={active} />
          <TestResults active={active} />
          <CertificateScreen active={active} />
          <Gate active={active} />
          <Sources active={active} />
          <Officer active={active} />
          <CaseScreen active={active} />
          <Policies active={active} />
          <ProofPack active={active} />
          <Masking active={active} />
          <Oversight active={active} />
          <AgentLog active={active} />
          </div>
          </CaseProvider>
        </AgentProvider>
      </UserProvider>
    </NavContext.Provider>
  );
}

export default function AppPage() {
  return (
    <Suspense fallback={null}>
      <AppShell />
    </Suspense>
  );
}
