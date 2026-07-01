"use client";

import { createContext, useContext, useState } from "react";

// The case the officer picked from the live caseload, so the Sensitive-data
// screen fetches that applicant's document. Defaults to the seeded demo case.
interface CaseCtx {
  caseRef: string;
  setCaseRef: (r: string) => void;
}

const Ctx = createContext<CaseCtx>({ caseRef: "HX-5821", setCaseRef: () => {} });

export function useCase() {
  return useContext(Ctx);
}

export function CaseProvider({ children }: { children: React.ReactNode }) {
  const [caseRef, setCaseRef] = useState("HX-5821");
  return <Ctx.Provider value={{ caseRef, setCaseRef }}>{children}</Ctx.Provider>;
}
