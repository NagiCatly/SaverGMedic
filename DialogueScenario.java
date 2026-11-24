/** Level-scaled first-aid decision scenarios. Educational game content: seek trained help. */
final class DialogueScenario {
    static final class Question {
        final String speaker, line, explanation;
        final String[] choices;
        final int correct;
        Question(String speaker, String line, String a, String b, String c, int correct, String explanation) {
            this.speaker=speaker; this.line=line; this.choices=new String[]{a,b,c};
            this.correct=correct; this.explanation=explanation;
        }
    }
    private static final String[] INJURIES = {
        "a sprained ankle", "a bleeding hand", "a possible arm fracture", "a burn",
        "a head injury", "a nosebleed", "a fainting episode", "a possible allergic reaction",
        "a deep cut", "a possible heat-related illness", "a chest injury", "an eye injury",
        "a possible seizure", "a painful knee injury", "a breathing problem", "a possible shock reaction",
        "a foot injury", "a possible neck injury", "a serious fall", "an unknown medical emergency"
    };
    static String injuryFor(int level, int patientNumber) {
        return INJURIES[(Math.max(0, patientNumber-1) + Math.max(0, level-1)*2) % INJURIES.length];
    }
    static int size() { return 3; }
    static Question get(int level, int patientNumber, int step) {
        String injury = injuryFor(level, patientNumber);
        String speaker = "Injured student";
        if (step == 0) {
            return new Question(speaker,
                "My friend has " + injury + ". What should we do first?",
                "Check that the area is safe, then approach calmly.",
                "Move the patient quickly without checking the area.",
                "Give medicine immediately, even if we do not know what happened.", 0,
                "Correct. Check for danger first and get a trained first aider or responsible adult.");
        }
        if (step == 1) {
            if (level <= 3) return new Question(speaker,
                "The patient is uncomfortable and frightened. What is the safest next step?",
                "Tell them to stand up and walk it off.",
                "Keep them calm and still, and ask a trained first aider to assess them.",
                "Leave them alone while you search for supplies.", 1,
                "Correct. Reassure the patient, avoid unnecessary movement, and get qualified help.");
            if (level <= 6) return new Question(speaker,
                "A bystander suggests trying a home remedy. How should you respond?",
                "Use it because it worked for someone else.",
                "Let several people try different treatments.",
                "Do not use unverified remedies; follow the first aider's instructions.", 2,
                "Correct. Avoid unverified treatment and follow guidance from a trained responder.");
            return new Question(speaker,
                "The patient's condition seems to be getting worse. What is the best decision?",
                "Alert emergency services or the designated emergency responder immediately, and stay with them.",
                "Wait several minutes to see whether it improves.",
                "Allow the patient to leave without checking their condition.", 0,
                "Correct. Worsening symptoms need urgent help from qualified responders.");
        }
        if (level <= 2) return new Question(speaker,
            "What should you tell the arriving first aider?",
            "Only your guess about the injury.",
            "A clear description of what happened and what you observed.",
            "Nothing; let them figure it out.", 1,
            "Correct. Share clear facts, observed symptoms, and any changes you noticed.");
        if (level <= 5) return new Question(speaker,
            "Two people are giving different instructions. What should you do?",
            "Follow whichever instruction sounds fastest.",
            "Try to perform both instructions at once.",
            "Let the trained first aider coordinate care and follow their directions.", 2,
            "Correct. One trained responder should coordinate care to avoid conflicting actions.");
        return new Question(speaker,
            "You must choose how to use your time while help is on the way. What is the best priority?",
            "Stay with the patient, monitor changes, and communicate updates to responders.",
            "Leave the patient unattended to look up random advice online.",
            "Promise the companion that everything is definitely fine.", 0,
            "Correct. Stay with the patient, observe changes, and pass accurate updates to responders.");
    }
    private DialogueScenario() {}
}
