"use client";

import { useEffect, useState } from "react";
import { useNav, ScreenId } from "@/lib/nav";

// The whole SwarmSight vision, told as one journey in three chapters. Each beat
// navigates to the screen that shows it, so the real UI is the illustration:
// first how an assistant earns the right to run, then a real case, then control.
const BEATS: {
  chapter: string;
  screen: ScreenId;
  title: string;
  body: string;
}[] = [
  {
    chapter: "Chapter 1 · Before an assistant is trusted",
    screen: "agents",
    title: "First, the assistant is set up",
    body: "A team registers the assistant they want to use, telling SwarmSight where it runs and, in plain terms, what it should do. At this point it is registered but trusted with nothing.",
  },
  {
    chapter: "Chapter 1 · Before an assistant is trusted",
    screen: "check",
    title: "Its powers are set, in plain checkboxes",
    body: "You describe what the assistant should do, and SwarmSight turns that into exact permissions: draft, request evidence, escalate are allowed; sending a decision or closing a case stay off, for a person. You tick only what it may do.",
  },
  {
    chapter: "Chapter 1 · Before an assistant is trusted",
    screen: "results",
    title: "Then it's safety-tested",
    body: "Before it touches real casework, the assistant is run through an arena of realistic cases built from the department's own policy. It has to handle the safe ones correctly and refuse the dangerous ones, like sending a decision to a citizen on its own.",
  },
  {
    chapter: "Chapter 1 · Before an assistant is trusted",
    screen: "certificate",
    title: "It earns a certificate, or it doesn't",
    body: "If it passes, it gets a certificate that states exactly what it is allowed to do, signed off by a person who is not its builder. No certificate, no live service.",
  },
  {
    chapter: "Chapter 1 · Before an assistant is trusted",
    screen: "gate",
    title: "A person signs off go-live",
    body: "Even certified, it only goes live once the source documents are ready and a service owner approves. Assurance first, then a human's go-ahead.",
  },
  {
    chapter: "Chapter 2 · A real case",
    screen: "masking",
    title: "An application arrives",
    body: "Now it's live. The Royal Borough of Kensington and Chelsea keeps its files in SharePoint. SwarmSight fetches Ms A. Adeyemi's housing application and hides the sensitive parts, her National Insurance number and medical notes, before the AI is ever allowed to see it.",
  },
  {
    chapter: "Chapter 2 · A real case",
    screen: "policies",
    title: "The rules come from real law",
    body: "The department's policy is built from actual legislation. It sets out, in plain rules, which cases an assistant may prepare and which a person must decide.",
  },
  {
    chapter: "Chapter 2 · A real case",
    screen: "department",
    title: "When the rules change, the assistants are flagged automatically",
    body: "The moment a new version of the rules takes effect, SwarmSight flags every assistant whose safety test was done under the old rules. Nobody has to remember: a flagged assistant must pass the test again before it is trusted under the new rules.",
  },
  {
    chapter: "Chapter 2 · A real case",
    screen: "case",
    title: "The assistant prepares, the rules catch a problem",
    body: "The AI drafts a response from only what it was allowed to see. But the policy spots an eviction risk with dependent children, so the case is held for a human instead of being sent.",
  },
  {
    chapter: "Chapter 2 · A real case",
    screen: "officer",
    title: "A person decides",
    body: "The officer reviews the draft, edits it, and approves. Nothing reaches the citizen without them, the assistant can never send on its own.",
  },
  {
    chapter: "Chapter 3 · Proof and control",
    screen: "audit",
    title: "Every step is provable",
    body: "The whole chain, what the AI saw, which policy applied, the human's edit, and the approval, is sealed on a tamper-proof record anyone can check later.",
  },
  {
    chapter: "Chapter 3 · Proof and control",
    screen: "oversight",
    title: "And it can be stopped instantly",
    body: "If an assistant ever misbehaves, the head of department can restrict what it does or suspend it entirely, taking effect immediately, and recorded like everything else.",
  },
];

export function StoryMode({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const { go } = useNav();
  const [step, setStep] = useState(0);

  // Reset to the first beat each time the tour is opened.
  useEffect(() => {
    if (open) setStep(0);
  }, [open]);

  // Navigate to the screen the current beat is about.
  useEffect(() => {
    if (open) go(BEATS[step].screen);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, step]);

  if (!open) return null;
  const beat = BEATS[step];
  const last = step === BEATS.length - 1;

  return (
    <div className="story-bar" role="dialog" aria-label="How SwarmSight works">
      <div className="story-inner">
        <div className="story-head">
          <span className="story-dots">
            {BEATS.map((_, i) => (
              <span key={i} className={`story-dot${i === step ? " on" : ""}`} />
            ))}
          </span>
          <span className="story-count">
            Step {step + 1} of {BEATS.length}
          </span>
          <button className="story-x" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>
        <div className="story-chapter">{beat.chapter}</div>
        <div className="story-title">{beat.title}</div>
        <div className="story-body">{beat.body}</div>
        <div className="story-controls">
          <button
            className="gov-btn g-grey"
            onClick={() => setStep((s) => Math.max(0, s - 1))}
            disabled={step === 0}
            style={step === 0 ? { opacity: 0.4, cursor: "default" } : undefined}
          >
            ← Back
          </button>
          {last ? (
            <button className="gov-btn g-green" onClick={onClose}>
              Finish ✓
            </button>
          ) : (
            <button
              className="gov-btn"
              onClick={() => setStep((s) => Math.min(BEATS.length - 1, s + 1))}
            >
              Next →
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
