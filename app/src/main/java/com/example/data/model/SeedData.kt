package com.example.data.model

object SeedData {

    fun defaultNotebooks(): List<NotebookEntity> = listOf(
        NotebookEntity(
            id = "nb_01",
            notebookNumber = 1,
            name = "PERSONALLY GENERAL",
            purpose = "A personal encyclopedia where you can collect knowledge about absolutely any subject.",
            shortDescription = "Science, nutrition, biology, AI, facts & personal ideas",
            detailedDescription = "Your unrestricted personal encyclopedia for capturing structured knowledge across biology, nutrition, technology, artificial intelligence, biochemistry, and curious ideas.",
            iconKey = "public",
            coverColorHex = 0xFF1B4332L,
            coverStyle = "LEATHER",
            sortOrder = 1,
            pinned = true,
            systemDefault = true,
            notebookType = "GENERAL"
        ),
        NotebookEntity(
            id = "nb_02",
            notebookNumber = 2,
            name = "WORD MEANINGS",
            purpose = "Improve English vocabulary and understand the meaning and practical usage of English words.",
            shortDescription = "Vocabulary, synonyms, antonyms, pronunciation & usage",
            detailedDescription = "Dedicated vocabulary ledger with structured word entries, phonetic pronunciation, Hindi & English meanings, synonyms, antonyms, and practical example sentences.",
            iconKey = "spellcheck",
            coverColorHex = 0xFF1E3A8AL,
            coverStyle = "LINEN",
            sortOrder = 2,
            systemDefault = true,
            notebookType = "VOCABULARY"
        ),
        NotebookEntity(
            id = "nb_03",
            notebookNumber = 3,
            name = "HOW TO SPEAK ENGLISH",
            purpose = "Learn practical spoken English, sentence formation, grammar, and natural communication.",
            shortDescription = "Sentence patterns, tenses, DF Sir lessons & fluency",
            detailedDescription = "Practical spoken English workbook focused on sentence formation, grammar for speaking, daily conversations, DF Sir lesson notes, and eliminating common mistakes.",
            iconKey = "record_voice_over",
            coverColorHex = 0xFF0F766EL,
            coverStyle = "CLASSIC",
            sortOrder = 3,
            systemDefault = true,
            notebookType = "SPOKEN_ENGLISH"
        ),
        NotebookEntity(
            id = "nb_04",
            notebookNumber = 4,
            name = "SOCIAL SKILLS & OTHER SKILLS",
            purpose = "Develop social intelligence, emotional abilities, cognitive abilities, and other useful human skills.",
            shortDescription = "Emotional intelligence, communication, memory & reasoning",
            detailedDescription = "Comprehensive playbook covering emotional awareness, impulse control, empathy, negotiation, public speaking, working memory, focus, and critical thinking.",
            iconKey = "psychology",
            coverColorHex = 0xFF6D28D9L,
            coverStyle = "LEATHER",
            sortOrder = 4,
            systemDefault = true,
            notebookType = "SKILLS"
        ),
        NotebookEntity(
            id = "nb_05",
            notebookNumber = 5,
            name = "FITNESS TRACKER",
            purpose = "Track physical training, hypertrophy, strength, and workout progress.",
            shortDescription = "Hypertrophy logs, sets, reps, weights, RIR, RPE & PRs",
            detailedDescription = "Structured training logbook for tracking hypertrophy exercises, working sets, repetitions, load progression, RIR/RPE intensity, and personal records.",
            iconKey = "fitness_center",
            coverColorHex = 0xFFB91C1CL,
            coverStyle = "SPIRAL",
            sortOrder = 5,
            pinned = true,
            systemDefault = true,
            notebookType = "FITNESS"
        ),
        NotebookEntity(
            id = "nb_06",
            notebookNumber = 6,
            name = "SOCIAL MEDIA",
            purpose = "Learn skills required to develop an online presence and earn through social media.",
            shortDescription = "Video editing, content strategy, branding & monetization",
            detailedDescription = "Creator and digital growth handbook covering video editing, photo editing, audience psychology, Instagram growth, personal branding, and monetization systems.",
            iconKey = "video_camera_front",
            coverColorHex = 0xFFC2410CL,
            coverStyle = "MINIMAL",
            sortOrder = 6,
            systemDefault = true,
            notebookType = "SOCIAL_MEDIA"
        ),
        NotebookEntity(
            id = "nb_07",
            notebookNumber = 7,
            name = "SAVED WORK",
            purpose = "A lifetime collection of information saved from messages, screenshots, and discoveries to work on later.",
            shortDescription = "WhatsApp saves, screenshots, gallery items & status tracking",
            detailedDescription = "Actionable archive for important messages, screenshots, gallery images, and online discoveries—organized by source and progress status.",
            iconKey = "bookmark_added",
            coverColorHex = 0xFF0369A1L,
            coverStyle = "LINEN",
            sortOrder = 7,
            systemDefault = true,
            notebookType = "SAVED_WORK"
        ),
        NotebookEntity(
            id = "nb_08",
            notebookNumber = 8,
            name = "HANDWRITING",
            purpose = "Improve handwriting quality, consistency, and writing speed.",
            shortDescription = "Letter formation, spacing, speed drills & progress",
            detailedDescription = "Practice journal for refining letter shapes, uniform word and letter spacing, stroke consistency, and timed writing speed.",
            iconKey = "edit_note",
            coverColorHex = 0xFF4338CAL,
            coverStyle = "SPIRAL",
            sortOrder = 8,
            systemDefault = true,
            notebookType = "HANDWRITING"
        ),
        NotebookEntity(
            id = "nb_09",
            notebookNumber = 9,
            name = "TO DO",
            purpose = "Maintain all tasks that you need to complete today, tomorrow, or at any future time.",
            shortDescription = "Today, tomorrow, upcoming, pending & important tasks",
            detailedDescription = "Action-oriented task notebook organizing Today, Tomorrow, Upcoming, Pending, Important, and Unscheduled tasks alongside detailed task notes.",
            iconKey = "checklist",
            coverColorHex = 0xFF15803DL,
            coverStyle = "CLASSIC",
            sortOrder = 9,
            pinned = true,
            systemDefault = true,
            notebookType = "TODO"
        ),
        NotebookEntity(
            id = "nb_10",
            notebookNumber = 10,
            name = "FINANCE",
            purpose = "Understand financial concepts and organize personal financial knowledge.",
            shortDescription = "Budgeting, saving, banking, taxes, inflation & investing",
            detailedDescription = "Personal wealth and financial literacy manual covering money management, emergency funds, interest, inflation, taxes, and long-term investing.",
            iconKey = "account_balance_wallet",
            coverColorHex = 0xFF92400EL,
            coverStyle = "LEATHER",
            sortOrder = 10,
            systemDefault = true,
            notebookType = "FINANCE"
        ),
        NotebookEntity(
            id = "nb_11",
            notebookNumber = 11,
            name = "LIFE LESSONS",
            purpose = "Understand life realities, human nature, relationships, human behavior, and different stages of human life.",
            shortDescription = "Life stages, human nature, family, time & real wisdom",
            detailedDescription = "This notebook is dedicated to understanding how life works, how people behave, how relationships develop, how responsibilities change with age, and what realities a person may encounter throughout life.",
            iconKey = "auto_stories",
            coverColorHex = 0xFF7C2D12L,
            coverStyle = "LEATHER",
            sortOrder = 11,
            systemDefault = true,
            notebookType = "LIFE_LESSONS"
        ),
        NotebookEntity(
            id = "nb_12",
            notebookNumber = 12,
            name = "OTHER",
            purpose = "A completely unrestricted notebook for anything that does not fit into the other notebooks.",
            shortDescription = "Open sandbox with zero mandatory categories",
            detailedDescription = "A freeform space with no mandatory structure for unique projects, miscellaneous collections, or cross-disciplinary thoughts.",
            iconKey = "folder_special",
            coverColorHex = 0xFF475569L,
            coverStyle = "MINIMAL",
            sortOrder = 12,
            systemDefault = true,
            notebookType = "OTHER"
        ),
        NotebookEntity(
            id = "nb_13",
            notebookNumber = 13,
            name = "ROUGH",
            purpose = "Temporary writing and rough work.",
            shortDescription = "Quick calculations, drafts, testing ideas & scratchpad",
            detailedDescription = "Fast, friction-free rough pad for quick calculations, temporary notes, early drafts, and unstructured brainstorming.",
            iconKey = "draw",
            coverColorHex = 0xFF57534EL,
            coverStyle = "SPIRAL",
            sortOrder = 13,
            systemDefault = true,
            notebookType = "ROUGH"
        )
    )

    private fun t(
        id: String,
        nbId: String,
        name: String,
        shortDesc: String,
        purpose: String,
        intro: String,
        covers: List<String>,
        concepts: List<String>,
        related: List<String>,
        order: Int,
        icon: String = "article"
    ) = TopicEntity(
        id = id,
        notebookId = nbId,
        name = name,
        shortDescription = shortDesc,
        purpose = purpose,
        introduction = intro,
        coversItems = covers,
        importantConcepts = concepts,
        relatedTopics = related,
        iconKey = icon,
        sortOrder = order
    )

    fun defaultTopics(): List<TopicEntity> {
        val list = mutableListOf<TopicEntity>()
        var idx = 1
        fun nextId(prefix: String) = "${prefix}_${idx++}"

        // NOTEBOOK 01 — PERSONALLY GENERAL
        val nb01Specs = listOf(
            Triple("Nutrition", "Macronutrients, micronutrients, hydration & energy balance", "Understand how nutrients fuel cellular health, metabolism, and long-term vitality."),
            Triple("Human Digestive System", "Mouth, esophagus, stomach, intestines & digestive enzymes", "Understand how the human body breaks down food, absorbs nutrients, and supports gut immunity."),
            Triple("Gut Microbiome", "Beneficial bacteria, diversity & gut-brain signaling", "Explore the ecosystem of trillions of microbes living in the digestive tract."),
            Triple("Gut Health", "Barrier integrity, prebiotics, probiotics & inflammation control", "Learn practical habits to maintain healthy digestion and microbial balance."),
            Triple("Cardiovascular Health", "Heart function, blood vessels, lipid profile & aerobic fitness", "Understand circulation, blood pressure regulation, and heart longevity."),
            Triple("Human Biology", "Cells, tissues, organ systems & homeostasis", "Build foundational knowledge of how the human organism is structured and regulated."),
            Triple("Human Physiology", "How organs and systems work together in real time", "Study mechanisms behind respiration, circulation, metabolism, and neural signaling."),
            Triple("Hormones", "Endocrine messengers regulating growth, mood & metabolism", "Understand insulin, cortisol, thyroid hormones, testosterone, and circadian signaling."),
            Triple("Thyroid Function", "T3, T4, TSH & metabolic rate regulation", "Understand how the thyroid gland controls energy expenditure and cellular metabolism."),
            Triple("Biochemistry", "Chemical reactions inside living cells", "Learn about enzymes, ATP energy, amino acids, lipids, and metabolic pathways."),
            Triple("Science", "Physics, chemistry, scientific method & natural laws", "Collect core scientific principles and evidence-based explanations."),
            Triple("Technology", "Computing, software systems, hardware & engineering", "Track how modern tools, networks, and devices operate."),
            Triple("Artificial Intelligence", "Machine learning, LLMs, neural networks & practical AI", "Understand how AI systems learn patterns and assist human work."),
            Triple("Interesting Facts", "Memorable discoveries, records & surprising truths", "Capture fascinating verified facts worth remembering."),
            Triple("Personal Ideas", "Original hypotheses, inventions & life observations", "Preserve your own creative sparks and cross-domain insights.")
        )
        nb01Specs.forEachIndexed { i, (name, shortDesc, purpose) ->
            val covers = when (name) {
                "Human Digestive System" -> listOf("Mouth", "Esophagus", "Stomach", "Small Intestine", "Large Intestine", "Digestive Enzymes", "Gut Microbiome", "Digestion and Absorption")
                "Nutrition" -> listOf("Macronutrients", "Micronutrients", "Proteins", "Carbohydrates", "Fats", "Vitamins", "Minerals", "Fiber", "Hydration", "Energy Balance")
                else -> listOf("Core Principles", "Key Mechanisms", "Practical Takeaways", "Real-World Applications")
            }
            val concepts = when (name) {
                "Human Digestive System" -> listOf("Mechanical & Chemical Digestion", "Enzymatic Breakdown", "Villi Nutrient Absorption", "Peristalsis", "Enteric Nervous System")
                "Nutrition" -> listOf("Protein Synthesis & Amino Acids", "Glycemic Response", "Essential Fatty Acids", "Electrolyte Balance", "Caloric Deficit & Surplus")
                else -> listOf("Foundational Definition", "Cause and Effect", "Key Terminology")
            }
            list += TopicEntity(
                id = if (name == "Human Digestive System") "topic_nb01_digestive" else if (name == "Nutrition") "topic_nb01_nutrition" else nextId("topic_nb01"),
                notebookId = "nb_01",
                name = name,
                shortDescription = shortDesc,
                purpose = purpose,
                introduction = "$name is a key area inside your Personal Encyclopedia. Use this structured page to study core concepts and attach your own notes, observations, and examples.",
                coversItems = covers,
                importantConcepts = concepts,
                relatedTopics = listOf("Human Biology", "Human Physiology", "Science").filter { it != name },
                iconKey = "science",
                sortOrder = i
            )
        }

        // NOTEBOOK 02 — WORD MEANINGS
        val nb02Names = listOf(
            "Daily Use English Words", "Modern English Vocabulary", "Advanced Vocabulary",
            "Difficult Words", "Synonyms", "Antonyms", "Pronunciation", "Word Usage",
            "Example Sentences", "Common Expressions", "Vocabulary Improvement"
        )
        nb02Names.forEachIndexed { i, name ->
            val covers = if (name == "Synonyms") {
                listOf("Meaning", "Purpose", "Types", "Examples", "Usage", "Related Vocabulary")
            } else {
                listOf("Core Meaning", "Pronunciation", "Nuance & Register", "Practical Usage", "Common Collocations")
            }
            list += t(
                id = if (name == "Synonyms") "topic_nb02_synonyms" else nextId("topic_nb02"),
                nbId = "nb_02",
                name = name,
                shortDesc = "Practical study of $name for stronger English command.",
                purpose = "Master $name so you recognize, remember, and use words accurately in speaking and writing.",
                intro = "$name helps bridge passive recognition and active vocabulary mastery. Pair word entries with Hindi equivalents and daily example sentences.",
                covers = covers,
                concepts = listOf("Context Clues", "Exact Nuance", "Spoken vs Written Register", "Active Recall"),
                related = listOf("Word Usage", "Example Sentences", "Synonyms").filter { it != name },
                order = i,
                icon = "spellcheck"
            )
        }

        // NOTEBOOK 03 — HOW TO SPEAK ENGLISH
        val nb03Names = listOf(
            "Spoken English", "Sentence Formation", "Grammar for Speaking",
            "Tenses in Practical Usage", "Daily Conversations", "Question Formation",
            "English Sentence Patterns", "Common Speaking Mistakes", "Fluency",
            "Listening Comprehension", "Natural English Expressions", "Conversation Practice"
        )
        nb03Names.forEachIndexed { i, name ->
            val covers = if (name == "Sentence Formation") {
                listOf("Purpose", "Basic Sentence Structure", "Subject", "Verb", "Object", "Questions", "Negative Sentences", "Practical Examples")
            } else {
                listOf("Core Structure", "DF Sir Lesson Notes", "Speaking Drills", "Hindi-to-English Mapping", "Real Dialogue Examples")
            }
            list += t(
                id = if (name == "Sentence Formation") "topic_nb03_sentence" else nextId("topic_nb03"),
                nbId = "nb_03",
                name = name,
                shortDesc = "Spoken fluency & natural structure for $name.",
                purpose = "Build effortless confidence in $name for everyday conversations and teacher-guided practice (including DF Sir lessons).",
                intro = "$name focuses on real-time spoken communication rather than rote textbook theory. Practice aloud with short sentence patterns.",
                covers = covers,
                concepts = listOf("Subject-Verb Agreement", "Natural Rhythm", "Think in English Patterns", "Active Speaking Practice"),
                related = listOf("Sentence Formation", "Daily Conversations", "Fluency").filter { it != name },
                order = i,
                icon = "record_voice_over"
            )
        }

        // NOTEBOOK 04 — SOCIAL SKILLS & OTHER SKILLS
        val nb04Names = listOf(
            "Emotional Intelligence", "Emotional Awareness", "Emotional Regulation",
            "Anger Control", "Impulse Control", "Empathy", "Understanding Other People's Emotions",
            "Interpersonal Communication", "Active Listening", "Assertiveness",
            "Verbal Communication", "Non-Verbal Communication", "Conflict Resolution",
            "Negotiation", "Public Speaking", "Memory", "Memory Recall",
            "Working Memory", "Attention", "Focus", "Executive Functions",
            "Reasoning", "Critical Thinking", "Problem Solving", "Decision Making", "Learning Ability"
        )
        nb04Names.forEachIndexed { i, name ->
            val covers = if (name == "Emotional Intelligence") {
                listOf("Definition", "Emotional Awareness", "Emotional Regulation", "Self-Awareness", "Empathy", "Social Awareness", "Practical Applications")
            } else {
                listOf("Definition", "Core Psychological Basis", "Real-Life Scenarios", "Actionable Habits", "Self-Assessment")
            }
            list += t(
                id = if (name == "Emotional Intelligence") "topic_nb04_ei" else nextId("topic_nb04"),
                nbId = "nb_04",
                name = name,
                shortDesc = "Practical mastery of $name in daily life and relationships.",
                purpose = "Develop measurable capability in $name for calmer reactions, clearer thinking, and stronger social connection.",
                intro = "$name is a learnable human skill. Combine theoretical understanding with personal reflections from real interactions.",
                covers = covers,
                concepts = listOf("Self-Observation", "Pause Before Reacting", "Clear Framing", "Deliberate Practice"),
                related = listOf("Emotional Intelligence", "Active Listening", "Critical Thinking").filter { it != name },
                order = i,
                icon = "psychology"
            )
        }

        // NOTEBOOK 05 — FITNESS TRACKER
        val nb05Names = listOf(
            "Hypertrophy Tracking", "Exercise Log", "Sets and Repetitions",
            "Training Weights", "RIR", "RPE", "Progressive Overload",
            "Personal Records", "Workout History", "Performance Notes"
        )
        nb05Names.forEachIndexed { i, name ->
            val covers = if (name == "Hypertrophy Tracking") {
                listOf("Exercise Selection", "Sets", "Repetitions", "RIR", "Training Volume", "Progressive Overload", "Performance Records")
            } else {
                listOf("Tracking Protocol", "Intensity Calibration", "Weekly Progression", "Recovery Signals")
            }
            list += t(
                id = if (name == "Hypertrophy Tracking") "topic_nb05_hypertrophy" else nextId("topic_nb05"),
                nbId = "nb_05",
                name = name,
                shortDesc = "Systematic tracking & principles of $name.",
                purpose = "Apply evidence-based training tracking to drive consistent strength and muscle hypertrophy.",
                intro = "$name records measurable training variables so every session builds deliberately on the previous one.",
                covers = covers,
                concepts = listOf("Reps In Reserve (RIR)", "Rate of Perceived Exertion (RPE)", "Mechanical Tension", "Volume Landmarks"),
                related = listOf("Hypertrophy Tracking", "Progressive Overload", "Personal Records").filter { it != name },
                order = i,
                icon = "fitness_center"
            )
        }

        // NOTEBOOK 06 — SOCIAL MEDIA
        val nb06Names = listOf(
            "Video Editing", "Photo Editing", "Content Creation", "Content Strategy",
            "Social Media Marketing", "Digital Marketing", "Personal Branding",
            "Instagram Growth", "Audience Psychology", "Engagement",
            "Online Appearance", "Content Research", "Monetization", "Other Digital Skills"
        )
        nb06Names.forEachIndexed { i, name ->
            val covers = if (name == "Video Editing") {
                listOf("Editing Fundamentals", "Timeline", "Cuts", "Transitions", "Audio", "Color Correction", "Export Settings")
            } else {
                listOf("Core Principles", "Workflow & Tools", "Audience Retention", "Analytics & Iteration")
            }
            list += t(
                id = if (name == "Video Editing") "topic_nb06_video" else nextId("topic_nb06"),
                nbId = "nb_06",
                name = name,
                shortDesc = "Creator playbook for $name.",
                purpose = "Build practical skill in $name to grow an authentic online presence and sustainable income.",
                intro = "$name turns attention into craft. Document hooks, pacing rules, editing workflows, and experiments.",
                covers = covers,
                concepts = listOf("Hook & Retention", "Storytelling Pacing", "Visual Consistency", "Value Delivery"),
                related = listOf("Video Editing", "Content Strategy", "Personal Branding").filter { it != name },
                order = i,
                icon = "video_camera_front"
            )
        }

        // NOTEBOOK 07 — SAVED WORK
        val nb07Names = listOf(
            "Saved Information", "WhatsApp Messages", "Screenshots", "Gallery Images",
            "Interesting Ideas", "Saved Messages", "Online Content",
            "Personal Discoveries", "Other Saved Information"
        )
        nb07Names.forEachIndexed { i, name ->
            val covers = if (name == "Saved Information") {
                listOf("Saved Source", "Original Content", "Description", "Priority", "Status", "Action Required")
            } else {
                listOf("Source Capture", "Context & Summary", "Action Status", "Follow-up Notes")
            }
            list += t(
                id = if (name == "Saved Information") "topic_nb07_saved" else nextId("topic_nb07"),
                nbId = "nb_07",
                name = name,
                shortDesc = "Organized backlog from $name.",
                purpose = "Store and process valuable items saved from $name so nothing important is lost.",
                intro = "Keep saved items actionable by tracking their source, priority, and status (Saved, Not Started, In Progress, Completed).",
                covers = covers,
                concepts = listOf("Capture Immediately", "Triage by Priority", "Convert to Action"),
                related = listOf("Saved Information", "Screenshots", "Interesting Ideas").filter { it != name },
                order = i,
                icon = "bookmark_added"
            )
        }

        // NOTEBOOK 08 — HANDWRITING
        val nb08Names = listOf(
            "Letter Formation", "Word Spacing", "Letter Spacing",
            "Writing Speed", "Writing Consistency", "Handwriting Techniques",
            "Practice Exercises", "Progress Records"
        )
        nb08Names.forEachIndexed { i, name ->
            val covers = if (name == "Letter Formation") {
                listOf("Letter Shape", "Consistency", "Spacing", "Writing Exercises", "Progress")
            } else {
                listOf("Posture & Grip", "Stroke Control", "Uniform Slant", "Timed Practice")
            }
            list += t(
                id = if (name == "Letter Formation") "topic_nb08_letter" else nextId("topic_nb08"),
                nbId = "nb_08",
                name = name,
                shortDesc = "Focused drills for $name.",
                purpose = "Improve legibility, neatness, rhythm, and speed through deliberate $name practice.",
                intro = "$name breaks penmanship into repeatable physical habits. Use ruled A4 practice sheets and log progress.",
                covers = covers,
                concepts = listOf("Baseline Alignment", "Consistent X-Height", "Relaxed Grip", "Controlled Speed"),
                related = listOf("Letter Formation", "Word Spacing", "Writing Consistency").filter { it != name },
                order = i,
                icon = "edit_note"
            )
        }

        // NOTEBOOK 09 — TO DO
        val nb09Names = listOf(
            "Today", "Tomorrow", "Upcoming Tasks", "Pending",
            "Completed", "Important Tasks", "Unscheduled Tasks"
        )
        nb09Names.forEachIndexed { i, name ->
            val covers = if (name == "Upcoming Tasks") {
                listOf("Task Description", "Priority", "Due Date", "Status")
            } else {
                listOf("Action Items", "Priority Sorting", "Time Blocking", "Completion Review")
            }
            list += t(
                id = if (name == "Upcoming Tasks") "topic_nb09_upcoming" else nextId("topic_nb09"),
                nbId = "nb_09",
                name = name,
                shortDesc = "Task queue and notes for $name.",
                purpose = "Keep clear visibility and execution momentum on all $name.",
                intro = "Combine structured task checklists with detailed planning pages for larger deliverables.",
                covers = covers,
                concepts = listOf("Next Physical Action", "Urgent vs Important", "Daily Review"),
                related = listOf("Today", "Upcoming Tasks", "Important Tasks").filter { it != name },
                order = i,
                icon = "checklist"
            )
        }

        // NOTEBOOK 10 — FINANCE
        val nb10Names = listOf(
            "Personal Finance", "Money Management", "Saving Money", "Budgeting",
            "Expenses", "Financial Planning", "Banking", "Interest",
            "Inflation", "Taxes", "Investments", "Financial Literacy"
        )
        nb10Names.forEachIndexed { i, name ->
            val covers = if (name == "Saving Money") {
                listOf("Purpose", "Saving Methods", "Budgeting", "Expenses", "Emergency Funds", "Financial Goals")
            } else {
                listOf("Core Concept", "Practical Rules", "Common Pitfalls", "Long-Term Impact")
            }
            list += t(
                id = if (name == "Saving Money") "topic_nb10_saving" else nextId("topic_nb10"),
                nbId = "nb_10",
                name = name,
                shortDesc = "Practical financial principles for $name.",
                purpose = "Understand $name clearly and apply disciplined habits to build financial security.",
                intro = "$name turns complex financial rules into straightforward personal decisions.",
                covers = covers,
                concepts = listOf("Compounding", "Real Purchasing Power", "Cash Flow Discipline", "Risk vs Return"),
                related = listOf("Saving Money", "Budgeting", "Investments").filter { it != name },
                order = i,
                icon = "account_balance_wallet"
            )
        }

        // NOTEBOOK 11 — LIFE LESSONS (All 21 exact topics!)
        val nb11Names = listOf(
            "Childhood", "Adolescence", "Youth", "Adulthood", "Old Age",
            "Health and Life", "Human Nature", "Human Psychology", "Relationships",
            "Friendship", "Marriage", "Family", "Society", "Wealth and Money Realities",
            "Success and Failure", "Time", "Aging", "Responsibilities",
            "Life Challenges", "Human Behavior", "Important Life Realities"
        )
        nb11Names.forEachIndexed { i, name ->
            val isChildhood = name == "Childhood"
            val covers = if (isChildhood) {
                listOf(
                    "Early Childhood Development", "Physical Growth", "Brain Development",
                    "Cognitive Development", "Emotional Development", "Language Development",
                    "Social Development", "Family Environment", "Childhood Experiences",
                    "Education and Learning", "Childhood Memories", "Influence of Parenting"
                )
            } else {
                listOf("Core Reality", "Psychological Patterns", "Practical Wisdom", "Long-Term Perspective", "Personal Observations")
            }
            val purpose = if (isChildhood) {
                "Understand the early stage of human life, including physical development, mental development, emotional development, learning, family influence, and early experiences."
            } else {
                "Understand the realities, patterns, responsibilities, and human lessons of $name."
            }
            val intro = if (isChildhood) {
                "Childhood is the early stage of human development. It plays an important role in shaping a person's physical abilities, cognitive development, emotional patterns, social behavior, and future personality."
            } else {
                "$name explores enduring truths about human life, behavior, relationships, and time. Use this page to capture timeless wisdom alongside your own lived experiences."
            }
            list += t(
                id = if (isChildhood) "topic_nb11_childhood" else nextId("topic_nb11"),
                nbId = "nb_11",
                name = name,
                shortDesc = if (isChildhood) "Early human development, family influence & formative years" else "Life realities & reflections on $name",
                purpose = purpose,
                intro = intro,
                covers = covers,
                concepts = if (isChildhood) {
                    listOf("Formative Environment", "Brain Plasticity", "Attachment & Trust", "Play-Based Learning")
                } else {
                    listOf("Cause & Consequence", "Perspective Across Time", "Human Incentives", "Acceptance & Responsibility")
                },
                related = listOf("Human Nature", "Family", "Time", "Responsibilities").filter { it != name },
                order = i,
                icon = "auto_stories"
            )
        }

        return list
    }

    fun defaultSubtopics(): List<SubtopicEntity> = listOf(
        SubtopicEntity(
            id = "sub_childhood_brain",
            notebookId = "nb_11",
            topicId = "topic_nb11_childhood",
            name = "Brain Development",
            shortDescription = "Rapid neural growth, synaptic pruning, and early learning plasticity.",
            purpose = "Understand how the human brain wires itself during childhood and how early environment shapes cognition.",
            introduction = "During the first years of life, the human brain forms millions of neural connections every second. Experiences, nutrition, sleep, and emotional safety determine which pathways strengthen.",
            coversItems = listOf("Synaptogenesis", "Neuroplasticity", "Critical Learning Windows", "Sleep & Memory Consolidation", "Stress Regulation"),
            importantConcepts = listOf("Use-It-or-Lose-It Pruning", "Serve-and-Return Interaction", "Executive Function Foundations"),
            relatedTopics = listOf("Cognitive Development", "Language Development", "Family Environment"),
            sortOrder = 0
        ),
        SubtopicEntity(
            id = "sub_childhood_emotional",
            notebookId = "nb_11",
            topicId = "topic_nb11_childhood",
            name = "Emotional Development",
            shortDescription = "How children learn emotional safety, regulation, and empathy.",
            purpose = "Explore how early emotional bonding shapes lifelong resilience and relationship patterns.",
            introduction = "Children learn how to understand and manage feelings by observing caregivers. Secure attachment provides the emotional base needed for curiosity and confidence.",
            coversItems = listOf("Attachment Styles", "Emotional Co-Regulation", "Naming Feelings", "Resilience Building"),
            importantConcepts = listOf("Emotional Safety", "Co-Regulation Before Self-Regulation", "Modeling Behavior"),
            relatedTopics = listOf("Influence of Parenting", "Social Development"),
            sortOrder = 1
        ),
        SubtopicEntity(
            id = "sub_digestive_stomach",
            notebookId = "nb_01",
            topicId = "topic_nb01_digestive",
            name = "Stomach & Digestive Enzymes",
            shortDescription = "Gastric acid, pepsin, and protein breakdown mechanics.",
            purpose = "Understand the chemical and mechanical role of the stomach in digestion.",
            introduction = "The stomach churns food with hydrochloric acid (HCl) and enzymes such as pepsin, converting solid food into chyme while neutralizing ingested microbes.",
            coversItems = listOf("Hydrochloric Acid", "Pepsinogen Activation", "Mucus Barrier", "Gastric Emptying"),
            importantConcepts = listOf("Low pH Denaturation", "Protein Cleavage", "Intrinsic Factor for B12"),
            relatedTopics = listOf("Small Intestine", "Gut Microbiome"),
            sortOrder = 0
        ),
        SubtopicEntity(
            id = "sub_sentence_svo",
            notebookId = "nb_03",
            topicId = "topic_nb03_sentence",
            name = "Basic Sentence Structure (S-V-O)",
            shortDescription = "Subject + Verb + Object foundations for clear spoken English.",
            purpose = "Build instant fluency by mastering the core English word order compared with Hindi (S-O-V).",
            introduction = "Hindi typically places the verb at the end (Subject + Object + Verb), whereas English places the verb directly after the subject (Subject + Verb + Object). Mastering this mental switch speeds up speaking.",
            coversItems = listOf("Subject Placement", "Helping & Main Verbs", "Object & Complement", "Time & Place Order"),
            importantConcepts = listOf("English S-V-O vs Hindi S-O-V", "Auxiliary Verbs (do/does/did)", "Natural Contractions"),
            relatedTopics = listOf("Question Formation", "Tenses in Practical Usage"),
            sortOrder = 0
        ),
        SubtopicEntity(
            id = "sub_hypertrophy_rir",
            notebookId = "nb_05",
            topicId = "topic_nb05_hypertrophy",
            name = "RIR & Training Volume",
            shortDescription = "Calibrating Reps in Reserve (0–3 RIR) for optimal muscle growth.",
            purpose = "Ensure working sets are close enough to muscular failure to stimulate hypertrophy without excessive fatigue.",
            introduction = "Reps in Reserve (RIR) measures how many clean repetitions you could still perform before momentary muscular failure. Most hypertrophy sets thrive around 1–3 RIR.",
            coversItems = listOf("0–3 RIR Sweet Spot", "Estimating Failure Accurately", "Weekly Sets Per Muscle", "Fatigue Management"),
            importantConcepts = listOf("Stimulating Reps", "RPE-to-RIR Relationship (RPE 8 = 2 RIR)", "Junk Volume Avoidance"),
            relatedTopics = listOf("Progressive Overload", "Personal Records"),
            sortOrder = 0
        )
    )

    fun defaultPages(): List<PageEntity> = listOf(
        PageEntity(
            id = "page_welcome_nb01",
            notebookId = "nb_01",
            topicId = "topic_nb01_digestive",
            title = "Digestive System Overview Notes",
            content = "Human Digestive System — Quick Study Sheet\n\n1. Mouth: Mechanical chewing + salivary amylase begins starch digestion.\n2. Esophagus: Peristalsis moves food to the stomach.\n3. Stomach: HCl + Pepsin break down proteins.\n4. Small Intestine: Primary site of nutrient absorption via villi.\n5. Large Intestine: Water absorption & gut microbiome fermentation.",
            pageNumber = 1,
            handwritingMode = false,
            fontKey = "lora",
            fontSizeSp = 17,
            paperColorKey = "IVORY"
        ),
        PageEntity(
            id = "page_childhood_nb11",
            notebookId = "nb_11",
            topicId = "topic_nb11_childhood",
            subtopicId = "sub_childhood_brain",
            title = "Brain Development Reflections",
            content = "Early years shape curiosity, emotional trust, and attention habits.\n\nKey reflection:\nSafe, calm environments give a child's brain room to explore instead of staying in survival mode.",
            pageNumber = 1,
            handwritingMode = true,
            fontKey = "kalam",
            fontSizeSp = 18,
            paperColorKey = "CREAM"
        ),
        PageEntity(
            id = "page_rough_nb13",
            notebookId = "nb_13",
            title = "Quick Rough Scratchpad",
            content = "Rough notes, quick calculations, and mixed Hindi-English drafts:\n\nआज का लक्ष्य: 30 minutes English speaking practice + workout log update.",
            pageNumber = 1,
            handwritingMode = true,
            fontKey = "kalam",
            fontSizeSp = 18,
            paperColorKey = "WHITE"
        )
    )

    fun defaultVocabulary(): List<VocabularyEntryEntity> = listOf(
        VocabularyEntryEntity(
            id = "voc_resilient",
            notebookId = "nb_02",
            word = "Resilient",
            pronunciation = "/rɪˈzɪljənt/",
            meaning = "Able to withstand or recover quickly from difficult conditions.",
            hindiMeaning = "लचीला / कठिन परिस्थितियों से जल्दी उबरने वाला",
            synonyms = listOf("Adaptable", "Tough", "Buoyant"),
            antonyms = listOf("Fragile", "Brittle"),
            exampleSentences = listOf(
                "A resilient mindset treats setbacks as temporary feedback.",
                "She stayed resilient throughout the demanding exam preparation."
            ),
            category = "Modern English Vocabulary"
        ),
        VocabularyEntryEntity(
            id = "voc_articulate",
            notebookId = "nb_02",
            word = "Articulate",
            pronunciation = "/ɑːrˈtɪkjəlɪt/",
            meaning = "Having or showing the ability to speak fluently and coherently.",
            hindiMeaning = "स्पष्ट वक्ता / साफ़ और प्रभावशाली बोलने वाला",
            synonyms = listOf("Eloquent", "Expressive", "Clear"),
            antonyms = listOf("Inarticulate", "Hesitant"),
            exampleSentences = listOf(
                "Practice daily sentence patterns to become a more articulate speaker."
            ),
            category = "Daily Use English Words"
        )
    )

    fun defaultFitnessLogs(): List<FitnessLogEntity> = listOf(
        FitnessLogEntity(
            id = "fit_incline_press",
            notebookId = "nb_05",
            exerciseName = "Incline Dumbbell Press",
            muscleGroup = "Upper Chest",
            sets = 3,
            reps = "10, 10, 9",
            weightKg = 25.0,
            rir = 2,
            rpe = 8.0,
            personalRecord = true,
            notes = "Full stretch at bottom, controlled 2-second eccentric."
        ),
        FitnessLogEntity(
            id = "fit_lat_pulldown",
            notebookId = "nb_05",
            exerciseName = "Neutral-Grip Lat Pulldown",
            muscleGroup = "Back (Lats)",
            sets = 4,
            reps = "12, 11, 10, 10",
            weightKg = 55.0,
            rir = 1,
            rpe = 9.0,
            personalRecord = false,
            notes = "Drive elbows down toward hips."
        )
    )

    fun defaultTodos(): List<TodoTaskEntity> = listOf(
        TodoTaskEntity(
            id = "todo_english_practice",
            notebookId = "nb_09",
            title = "Practice 15 spoken English sentence patterns",
            description = "Review S-V-O structure and record 2 minutes of spoken practice.",
            category = "TODAY",
            priority = "HIGH",
            completed = false
        ),
        TodoTaskEntity(
            id = "todo_finance_review",
            notebookId = "nb_09",
            title = "Review monthly savings & emergency fund target",
            description = "Update Notebook 10 — Finance budget page.",
            category = "UPCOMING",
            priority = "MEDIUM",
            completed = false
        )
    )

    fun defaultSavedWork(): List<AttachmentEntity> = listOf(
        AttachmentEntity(
            id = "att_starter_01",
            notebookId = "nb_07",
            fileName = "Daily Learning Workflow Note",
            fileType = "DOCUMENT",
            localUriOrPath = "",
            caption = "Use Photo Picker inside Saved Work to attach screenshots, WhatsApp exports, or gallery photos to review later.",
            sourceLabel = "Interesting Ideas",
            status = "IN_PROGRESS",
            priority = "MEDIUM"
        )
    )
}
