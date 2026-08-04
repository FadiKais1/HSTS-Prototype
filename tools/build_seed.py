#!/usr/bin/env python3
"""Builds database/seed_demo_data.sql.

The demo database has to do two jobs. It must be large and varied enough that
every use case in the customer story can be shown without inventing data during
a presentation, and it must be readable, so that a name in a report is a person
and a question in an exam is a real question about a real topic.

Every identifier follows the encoded scheme: a question is its two digit course
number then its three digit number within that course, and an exam is its
subject number, then its course number, then its number within that course.
"""

import base64
import hashlib
import os
import random
import uuid

ITERATIONS = 210_000


def password_hash(password: str) -> str:
    """PBKDF2-SHA256 in the format hsts.server.security.PasswordHasher reads."""
    salt = os.urandom(16)
    derived = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, ITERATIONS, 32)
    return (
        f"pbkdf2-sha256${ITERATIONS}$"
        f"{base64.b64encode(salt).decode()}${base64.b64encode(derived).decode()}"
    )


def sql_text(value: str) -> str:
    return value.replace("'", "''")


# --------------------------------------------------------------------- people

TEACHER_PASSWORD = "Teacher!2026"
STUDENT_PASSWORD = "Student!2026"
COORDINATOR_PASSWORD = "Coordinator!2026"
PRINCIPAL_PASSWORD = "Principal!2026"

# (user_id, full name, email local part, role)
STAFF = [
    (1101, "Rania Haddad", "rania.haddad", "TEACHER"),
    (1102, "Yosef Mizrahi", "yosef.mizrahi", "TEACHER"),
    (1103, "Dalia Ben-Ami", "dalia.benami", "TEACHER"),
    (1104, "Samir Khoury", "samir.khoury", "TEACHER"),
    (1105, "Efrat Golan", "efrat.golan", "TEACHER"),
    (1106, "Lina Masalha", "lina.masalha", "TEACHER"),
    (1201, "Michal Avrahami", "michal.avrahami", "COORDINATOR"),
    (1202, "Nabil Zoabi", "nabil.zoabi", "COORDINATOR"),
    (1301, "Orit Shalev", "orit.shalev", "PRINCIPAL"),
]

STUDENT_NAMES = [
    "Noa Levi", "Maya Cohen", "Tamar Mizrahi", "Yael Haddad", "Shira Peretz",
    "Rotem Azoulay", "Adi Barkat", "Hila Nissim", "Lior Segal", "Talia Ohayon",
    "Sivan Dahan", "Michal Katz", "Roni Amar", "Gal Shapira", "Inbar Elbaz",
    "Naomi Tal", "Ayelet Hazan", "Dana Sasson", "Yasmin Nasser", "Lama Zoabi",
    "Rania Awad", "Salma Diab", "Hiba Mansour", "Nour Khalil", "Rasha Saleh",
    "Ella Friedman", "Zohar Ben-David", "Ofri Malka", "Carmel Weiss", "Neta Yosef",
    "Avigail Ronen", "Bar Shmueli", "Chen Halevi", "Daniella Aviv", "Efrat Simon",
    "Gefen Arad", "Hodaya Amsalem", "Idit Perez", "Keren Solomon", "Liat Turgeman",
]

FIRST_STUDENT_ID = 2101


def students():
    return [
        (FIRST_STUDENT_ID + index, name)
        for index, name in enumerate(STUDENT_NAMES)
    ]


def email_for(name: str) -> str:
    parts = name.lower().replace("-", "").split()
    return f"{parts[0]}.{parts[-1]}@hsts.local"


# ------------------------------------------------------------------- teaching

# (subject_id, subject_number, code, name)
SUBJECTS = [
    (11, "01", "MATH", "Mathematics"),
    (12, "02", "PHYS", "Physics"),
    (13, "03", "ENGL", "English"),
    (14, "04", "BIOL", "Biology"),
    (15, "05", "HIST", "History"),
    (16, "06", "CHEM", "Chemistry"),
]

# (course_id, subject_id, course_number, code, name, grade)
COURSES = [
    (21, 11, "01", "MATH-10A", "Mathematics - Grade 10", "Grade 10"),
    (22, 11, "02", "MATH-11A", "Mathematics - Grade 11", "Grade 11"),
    (23, 11, "03", "MATH-12A", "Mathematics - Grade 12", "Grade 12"),
    (24, 12, "04", "PHYS-10A", "Physics - Grade 10", "Grade 10"),
    (25, 12, "05", "PHYS-11A", "Physics - Grade 11", "Grade 11"),
    (26, 13, "06", "ENGL-10A", "English - Grade 10", "Grade 10"),
    (27, 13, "07", "ENGL-11A", "English - Grade 11", "Grade 11"),
    (28, 14, "08", "BIOL-10A", "Biology - Grade 10", "Grade 10"),
    (29, 15, "09", "HIST-10A", "History - Grade 10", "Grade 10"),
    (30, 16, "10", "CHEM-11A", "Chemistry - Grade 11", "Grade 11"),
]

# Every course is taught by at least two teachers (requirement 15), and several
# teachers hold courses in more than one subject so the subject picker matters.
TEACHER_COURSES = [
    (1101, 21), (1101, 22), (1101, 24),          # Rania: Maths and Physics
    (1102, 26), (1102, 27), (1102, 21),          # Yosef: English and Maths
    (1103, 22), (1103, 23), (1103, 30),          # Dalia: Maths and Chemistry
    (1104, 24), (1104, 25), (1104, 29),          # Samir: Physics and History
    (1105, 28), (1105, 30), (1105, 23),          # Efrat: Biology, Chemistry, Maths
    (1106, 26), (1106, 29), (1106, 27),          # Lina: English and History
    (1201, 21), (1201, 22), (1201, 23),          # Michal coordinates the sciences
    (1201, 24), (1201, 25), (1201, 28), (1201, 30),
    (1202, 26), (1202, 27), (1202, 29),          # Nabil coordinates the humanities
]

SUBJECT_COORDINATORS = [
    (11, 1201), (12, 1201), (14, 1201), (16, 1201),
    (13, 1202), (15, 1202),
]


# -------------------------------------------------------------- question bank

# course_id -> (topic, text, options, correct index 1-4, difficulty)
QUESTIONS = {
    21: [  # Mathematics Grade 10
        ("Algebra", "Solve for x: 3x + 7 = 22", ["x = 3", "x = 5", "x = 7", "x = 15"], 2, "EASY"),
        ("Algebra", "Solve for x: 2(x - 4) = 10", ["x = 3", "x = 7", "x = 9", "x = 12"], 3, "EASY"),
        ("Algebra", "What is the discriminant of x^2 - 6x + 9?", ["-36", "0", "9", "36"], 2, "MEDIUM"),
        ("Algebra", "How many real roots does x^2 + 4 = 0 have?", ["0", "1", "2", "4"], 1, "MEDIUM"),
        ("Algebra", "Factorise x^2 - 9", ["(x-3)(x+3)", "(x-9)(x+1)", "(x-3)^2", "x(x-9)"], 1, "EASY"),
        ("Algebra", "Simplify 12/18", ["1/2", "2/3", "3/4", "6/9"], 2, "EASY"),
        ("Geometry", "Interior angles of a triangle sum to", ["90", "180", "270", "360"], 2, "EASY"),
        ("Geometry", "Area of a circle with radius 4 is closest to", ["12.6", "25.1", "50.3", "64.0"], 3, "MEDIUM"),
        ("Geometry", "Hypotenuse of a right triangle with legs 6 and 8", ["10", "12", "14", "48"], 1, "EASY"),
        ("Geometry", "Circumference of a circle with diameter 10 is closest to", ["15.7", "31.4", "78.5", "100"], 2, "MEDIUM"),
        ("Geometry", "A regular hexagon has interior angles of", ["108", "120", "135", "150"], 2, "HARD"),
        ("Functions", "The slope of y = 4x - 3 is", ["-3", "1", "3", "4"], 4, "EASY"),
        ("Functions", "Where does y = 2x + 6 cross the x axis?", ["x = -3", "x = 0", "x = 3", "x = 6"], 1, "MEDIUM"),
        ("Functions", "f(x) = x^2 - 2x has a minimum at x =", ["0", "1", "2", "-1"], 2, "HARD"),
        ("Statistics", "The median of 3, 7, 9, 15, 21 is", ["7", "9", "11", "15"], 2, "EASY"),
        ("Statistics", "The mean of 4, 8, 10, 18 is", ["8", "9", "10", "12"], 3, "EASY"),
    ],
    22: [  # Mathematics Grade 11
        ("Trigonometry", "sin^2 x + cos^2 x equals", ["0", "1", "2", "sin 2x"], 2, "EASY"),
        ("Trigonometry", "sin(90 degrees) equals", ["0", "0.5", "1", "undefined"], 3, "EASY"),
        ("Trigonometry", "cos(0 degrees) equals", ["-1", "0", "0.5", "1"], 4, "EASY"),
        ("Trigonometry", "tan x is undefined at", ["0", "45", "90", "180"], 3, "MEDIUM"),
        ("Sequences", "The 10th term of a_n = 3n - 1 is", ["27", "29", "30", "31"], 2, "EASY"),
        ("Sequences", "Common difference of 5, 9, 13, 17", ["3", "4", "5", "9"], 2, "EASY"),
        ("Sequences", "Next term of 2, 4, 8, 16", ["18", "24", "32", "64"], 3, "EASY"),
        ("Calculus", "The derivative of sin x is", ["cos x", "-cos x", "sin x", "-sin x"], 1, "MEDIUM"),
        ("Calculus", "The derivative of x^3 is", ["x^2", "2x^2", "3x^2", "3x"], 3, "MEDIUM"),
        ("Calculus", "The derivative of a constant is", ["0", "1", "the constant", "undefined"], 1, "EASY"),
        ("Calculus", "The integral of 2x with respect to x is", ["x^2 + C", "2 + C", "x + C", "2x^2 + C"], 1, "HARD"),
        ("Vectors", "The magnitude of the vector (3, 4) is", ["5", "7", "12", "25"], 1, "MEDIUM"),
    ],
    23: [  # Mathematics Grade 12
        ("Calculus", "A function is increasing where its derivative is", ["negative", "zero", "positive", "undefined"], 3, "MEDIUM"),
        ("Calculus", "At a local maximum the first derivative is", ["positive", "negative", "zero", "undefined"], 3, "MEDIUM"),
        ("Calculus", "The second derivative describes", ["slope", "area", "concavity", "period"], 3, "HARD"),
        ("Probability", "Probability of two heads on two fair coins", ["1/8", "1/4", "1/2", "3/4"], 2, "MEDIUM"),
        ("Probability", "Probability of rolling a 6 on a fair die", ["1/12", "1/6", "1/3", "1/2"], 2, "EASY"),
        ("Probability", "Two events are independent when", ["they cannot both occur", "one does not affect the other", "they are equally likely", "they sum to one"], 2, "HARD"),
        ("Complex numbers", "i squared equals", ["-1", "0", "1", "i"], 1, "MEDIUM"),
        ("Complex numbers", "The modulus of 3 + 4i is", ["3", "4", "5", "7"], 3, "HARD"),
    ],
    24: [  # Physics Grade 10
        ("Mechanics", "Newton's second law is written", ["F = mv", "F = ma", "F = m/a", "F = a/m"], 2, "EASY"),
        ("Mechanics", "For every action there is", ["no reaction", "a smaller reaction", "an equal and opposite reaction", "a delayed reaction"], 3, "EASY"),
        ("Mechanics", "An object with no net force acting on it", ["always stops", "keeps its velocity", "accelerates", "falls"], 2, "MEDIUM"),
        ("Mechanics", "Velocity after time t under constant acceleration is", ["v = u + at", "v = ut", "v = at^2", "v = u/a"], 1, "MEDIUM"),
        ("Mechanics", "The SI unit of force is the", ["joule", "watt", "newton", "pascal"], 3, "EASY"),
        ("Energy", "Kinetic energy is", ["mgh", "0.5mv^2", "mv", "Fd/t"], 2, "MEDIUM"),
        ("Energy", "The SI unit of energy is the", ["newton", "joule", "watt", "volt"], 2, "EASY"),
        ("Energy", "Power is energy divided by", ["mass", "distance", "time", "force"], 3, "EASY"),
        ("Waves", "The speed of a wave equals", ["frequency x wavelength", "frequency / wavelength", "wavelength / period", "amplitude x frequency"], 1, "MEDIUM"),
        ("Waves", "Sound cannot travel through", ["water", "steel", "air", "a vacuum"], 4, "EASY"),
    ],
    25: [  # Physics Grade 11
        ("Electricity", "Ohm's law states", ["V = IR", "V = I/R", "I = VR", "R = VI"], 1, "EASY"),
        ("Electricity", "The SI unit of resistance is the", ["volt", "ampere", "ohm", "coulomb"], 3, "EASY"),
        ("Electricity", "Resistors in series have a total resistance equal to", ["their sum", "their product", "their average", "the smallest"], 1, "MEDIUM"),
        ("Electricity", "Current is the rate of flow of", ["energy", "charge", "voltage", "resistance"], 2, "MEDIUM"),
        ("Thermodynamics", "Heat flows from", ["cold to hot", "hot to cold", "high to low pressure", "solid to gas"], 2, "EASY"),
        ("Thermodynamics", "Absolute zero is", ["0 C", "-100 C", "-273 C", "-373 C"], 3, "MEDIUM"),
        ("Optics", "Light bends when entering a new medium because its", ["frequency changes", "speed changes", "colour changes", "amplitude changes"], 2, "HARD"),
        ("Optics", "The angle of incidence equals the angle of", ["refraction", "reflection", "diffraction", "dispersion"], 2, "EASY"),
    ],
    26: [  # English Grade 10
        ("Grammar", "Choose the correct form: She ___ to school every day.", ["go", "goes", "going", "gone"], 2, "EASY"),
        ("Grammar", "The past tense of write is", ["writed", "wrote", "written", "writing"], 2, "EASY"),
        ("Grammar", "Which word is an adverb?", ["quick", "quickly", "quickness", "quicken"], 2, "EASY"),
        ("Grammar", "Identify the preposition: The book is on the table.", ["book", "is", "on", "table"], 3, "MEDIUM"),
        ("Vocabulary", "A synonym for 'begin' is", ["end", "commence", "delay", "avoid"], 2, "EASY"),
        ("Vocabulary", "An antonym for 'generous' is", ["kind", "wealthy", "selfish", "cheerful"], 3, "MEDIUM"),
        ("Vocabulary", "'Revise' most nearly means", ["copy", "improve", "delete", "print"], 2, "MEDIUM"),
        ("Reading", "The main idea of a passage is its", ["first word", "central message", "longest sentence", "title only"], 2, "MEDIUM"),
        ("Reading", "An inference is a conclusion drawn from", ["evidence in the text", "the title alone", "personal opinion only", "the author's name"], 1, "HARD"),
    ],
    27: [  # English Grade 11
        ("Literature", "A metaphor compares two things", ["using like or as", "directly, without like or as", "by counting them", "by rhyming them"], 2, "MEDIUM"),
        ("Literature", "The narrator who uses 'I' is writing in", ["first person", "second person", "third person", "omniscient"], 1, "EASY"),
        ("Literature", "Alliteration repeats", ["vowel sounds", "initial consonant sounds", "whole words", "line lengths"], 2, "MEDIUM"),
        ("Writing", "A thesis statement belongs in the", ["introduction", "middle paragraph", "conclusion only", "bibliography"], 1, "MEDIUM"),
        ("Writing", "A paragraph should usually develop", ["several unrelated ideas", "one main idea", "no main idea", "only dialogue"], 2, "EASY"),
        ("Writing", "Which is a formal register?", ["gonna", "wanna", "shall not", "ain't"], 3, "MEDIUM"),
    ],
    28: [  # Biology Grade 10
        ("Cells", "The powerhouse of the cell is the", ["nucleus", "ribosome", "mitochondrion", "vacuole"], 3, "EASY"),
        ("Cells", "Photosynthesis takes place in the", ["mitochondrion", "chloroplast", "nucleus", "membrane"], 2, "EASY"),
        ("Cells", "DNA is stored mainly in the", ["cytoplasm", "nucleus", "cell wall", "vacuole"], 2, "EASY"),
        ("Genetics", "A gene is a section of", ["protein", "DNA", "lipid", "carbohydrate"], 2, "MEDIUM"),
        ("Genetics", "How many chromosomes does a human body cell carry?", ["23", "42", "46", "48"], 3, "MEDIUM"),
        ("Genetics", "An organism with two identical alleles is", ["heterozygous", "homozygous", "recessive", "mutant"], 2, "HARD"),
        ("Ecology", "Organisms that make their own food are", ["consumers", "producers", "decomposers", "predators"], 2, "EASY"),
        ("Ecology", "Energy enters most ecosystems from", ["the soil", "the sun", "water", "the air"], 2, "EASY"),
    ],
    29: [  # History Grade 10
        ("Modern history", "The First World War began in", ["1905", "1914", "1918", "1939"], 2, "EASY"),
        ("Modern history", "The Second World War ended in", ["1943", "1944", "1945", "1946"], 3, "EASY"),
        ("Modern history", "The State of Israel was declared in", ["1918", "1945", "1948", "1967"], 3, "EASY"),
        ("Modern history", "The Industrial Revolution began in", ["France", "Britain", "Germany", "Russia"], 2, "MEDIUM"),
        ("Sources", "A primary source is one that is", ["written long afterwards", "created at the time", "always printed", "always neutral"], 2, "MEDIUM"),
        ("Sources", "Historians check reliability by", ["reading one source closely", "comparing several sources", "trusting the oldest", "trusting the longest"], 2, "HARD"),
    ],
    30: [  # Chemistry Grade 11
        ("Atoms", "The atomic number counts", ["neutrons", "protons", "electrons and neutrons", "the mass"], 2, "EASY"),
        ("Atoms", "Isotopes differ in their number of", ["protons", "electrons", "neutrons", "shells"], 3, "MEDIUM"),
        ("Bonding", "A covalent bond involves", ["transferring electrons", "sharing electrons", "losing protons", "sharing neutrons"], 2, "MEDIUM"),
        ("Bonding", "Sodium chloride is held together by", ["covalent bonds", "ionic bonds", "hydrogen bonds", "metallic bonds"], 2, "MEDIUM"),
        ("Reactions", "A reaction that releases heat is", ["endothermic", "exothermic", "catalytic", "reversible"], 2, "EASY"),
        ("Reactions", "The pH of a neutral solution is", ["0", "5", "7", "14"], 3, "EASY"),
        ("Reactions", "A catalyst changes the reaction rate by", ["being consumed", "lowering activation energy", "raising temperature", "adding mass"], 2, "HARD"),
    ],
}


# ------------------------------------------------------------------- exams

# (exam_id, course_id, author, title, status, exam_number, questions used)
# Statuses deliberately cover the whole approval lifecycle so a demo can show
# a draft being submitted, one waiting for a coordinator, one approved and one
# sent back with a reason.
EXAMS = [
    (41, 21, 1101, "Algebra Midterm", "APPROVED", 1, 10),
    (42, 21, 1101, "Algebra Final", "APPROVED", 2, 10),
    (43, 21, 1102, "Geometry Practice", "APPROVED", 3, 8),
    (44, 21, 1101, "Algebra Retake", "PENDING_APPROVAL", 4, 8),
    (45, 21, 1102, "Statistics Starter", "DRAFT", 5, 6),
    (46, 22, 1103, "Trigonometry Midterm", "APPROVED", 1, 8),
    (47, 22, 1101, "Calculus Basics", "APPROVED", 2, 8),
    (48, 22, 1103, "Sequences Quiz", "REJECTED", 3, 6),
    (49, 23, 1105, "Probability Test", "APPROVED", 1, 6),
    (50, 24, 1104, "Mechanics Midterm", "APPROVED", 1, 8),
    (51, 24, 1101, "Energy and Waves", "APPROVED", 2, 8),
    (52, 24, 1104, "Forces Retake", "PENDING_APPROVAL", 3, 6),
    (53, 25, 1104, "Electricity Test", "APPROVED", 1, 6),
    (54, 26, 1102, "Grammar Midterm", "APPROVED", 1, 8),
    (55, 26, 1106, "Vocabulary Quiz", "APPROVED", 2, 6),
    (56, 27, 1106, "Literature Test", "APPROVED", 1, 6),
    (57, 28, 1105, "Cells and Genetics", "APPROVED", 1, 8),
    (58, 29, 1104, "Modern History Test", "APPROVED", 1, 6),
    (59, 30, 1103, "Atoms and Bonding", "APPROVED", 1, 7),
    (60, 22, 1103, "Vectors Practice", "DRAFT", 4, 6),
]

# (execution_id, exam_id, code, creator, opening offset days, duration, state)
# state: PAST closed and graded, TODAY open now, FUTURE still scheduled.
EXECUTIONS = [
    (71, 41, "M101", 1101, -28, 60, "PAST"),
    (72, 42, "M102", 1101, -21, 75, "PAST"),
    (73, 43, "M103", 1102, -14, 45, "PAST"),
    (74, 46, "M201", 1103, -18, 60, "PAST"),
    (75, 47, "M202", 1101, -11, 60, "PAST"),
    (76, 50, "P101", 1104, -25, 60, "PAST"),
    (77, 51, "P102", 1101, -9, 50, "PAST"),
    (78, 53, "P201", 1104, -7, 45, "PAST"),
    (79, 54, "E101", 1102, -16, 45, "PAST"),
    (80, 55, "E102", 1106, -6, 30, "PAST"),
    (81, 57, "B101", 1105, -12, 60, "PAST"),
    (82, 58, "H101", 1104, -5, 40, "PAST"),
    (83, 59, "C101", 1103, -4, 50, "PAST"),
    (84, 49, "M301", 1105, -3, 45, "PAST"),
    (85, 41, "M104", 1102, 0, 60, "TODAY"),
    (86, 50, "P103", 1101, 0, 60, "TODAY"),
    (87, 54, "E103", 1106, 0, 45, "TODAY"),
    (88, 42, "M105", 1101, 2, 75, "FUTURE"),
    (89, 56, "E201", 1106, 3, 45, "FUTURE"),
    (90, 51, "P104", 1104, 5, 50, "FUTURE"),
]


# --------------------------------------------------------------- enrolment

def enrolments():
    """Each student takes the core courses for her year plus a few electives.

    Enrolment is uneven on purpose. A report on a course should not simply list
    every student in the school, and a teacher scheduling an exam should see a
    class rather than a year group.
    """
    rows = []
    for index, (user_id, _name) in enumerate(students()):
        year = index % 3  # 0 -> grade 10, 1 -> grade 11, 2 -> grade 12
        if year == 0:
            courses = [21, 24, 26, 28, 29]
        elif year == 1:
            courses = [22, 25, 27, 30]
        else:
            courses = [23, 22, 27]
        # A handful of students take an extra subject.
        if index % 7 == 0:
            courses.append(30)
        if index % 5 == 0 and 21 not in courses:
            courses.append(21)
        rows.extend((user_id, course) for course in dict.fromkeys(courses))
    return rows


def students_in(course_id, enrolment_rows):
    return [student for student, course in enrolment_rows if course == course_id]


# --------------------------------------------------------------------- output

def build():
    out = []
    add = out.append

    add("-- =============================================================")
    add("-- HSTS demo seed data")
    add("-- Generated by tools/build_seed.py. Edit that script, not this file.")
    add("-- Run AFTER database/init.sql on a freshly created database.")
    add("--")
    add("-- Demo credentials")
    add(f"--   Teachers      <first>.<last>@hsts.local  /  {TEACHER_PASSWORD}")
    add(f"--   Coordinators  <first>.<last>@hsts.local  /  {COORDINATOR_PASSWORD}")
    add(f"--   Principal     orit.shalev@hsts.local     /  {PRINCIPAL_PASSWORD}")
    add(f"--   Students      <first>.<last>@hsts.local  /  {STUDENT_PASSWORD}")
    add("--")
    add("-- A student's identity confirmation number is 30000 followed by her")
    add("-- user id, so Noa Levi (2101) confirms with 300002101.")
    add("--")
    add("-- Identifiers follow the encoded scheme: a question is its two digit")
    add("-- course number then its three digit number within that course, and an")
    add("-- exam is its subject number, course number, then exam number.")
    add("-- =============================================================")
    add("")
    add("USE hsts_prototype;")
    add("")
    add("SET @seed_now = NOW();")
    add("")

    # ---- subjects and courses
    add("-- ---------- Subjects ----------")
    add("INSERT IGNORE INTO subjects (subject_id, subject_code, subject_number, name, description) VALUES")
    add(",\n".join(
        f"  ({sid}, '{code}', '{number}', '{sql_text(name)}', NULL)"
        for sid, number, code, name in SUBJECTS
    ) + ";")
    add("")

    add("-- ---------- Courses ----------")
    add("INSERT IGNORE INTO courses")
    add("    (course_id, subject_id, course_code, course_number, name, grade_level, school_year) VALUES")
    add(",\n".join(
        f"  ({cid}, {sid}, '{code}', '{number}', '{sql_text(name)}', '{grade}', '2026')"
        for cid, sid, number, code, name, grade in COURSES
    ) + ";")
    add("")

    # ---- people
    add("-- ---------- Staff ----------")
    add("INSERT IGNORE INTO users (user_id, full_name, email, password_hash, role, status) VALUES")
    staff_rows = []
    for user_id, name, local, role in STAFF:
        password = {
            "TEACHER": TEACHER_PASSWORD,
            "COORDINATOR": COORDINATOR_PASSWORD,
            "PRINCIPAL": PRINCIPAL_PASSWORD,
        }[role]
        staff_rows.append(
            f"  ({user_id}, '{sql_text(name)}', '{local}@hsts.local', "
            f"'{password_hash(password)}', '{role}', 'ACTIVE')"
        )
    add(",\n".join(staff_rows) + ";")
    add("")

    add("-- ---------- Students ----------")
    add("INSERT IGNORE INTO users (user_id, full_name, email, password_hash, role, status) VALUES")
    add(",\n".join(
        f"  ({user_id}, '{sql_text(name)}', '{email_for(name)}', "
        f"'{password_hash(STUDENT_PASSWORD)}', 'STUDENT', 'ACTIVE')"
        for user_id, name in students()
    ) + ";")
    add("")

    add("-- ---------- Student identity numbers ----------")
    add("-- Confirmed when starting an exam; 30000 followed by the user id.")
    add("INSERT IGNORE INTO student_profiles (user_id, identity_number_hash) VALUES")
    add(",\n".join(
        f"  ({user_id}, '{password_hash(f'30000{user_id}')}')"
        for user_id, _name in students()
    ) + ";")
    add("")

    add("-- ---------- Teaching assignments (requirement 15) ----------")
    add("-- Every course is taught by at least two members of staff.")
    add("INSERT IGNORE INTO teacher_courses (teacher_user_id, course_id) VALUES")
    add(",\n".join(f"  ({t}, {c})" for t, c in TEACHER_COURSES) + ";")
    add("")

    add("-- ---------- Subject coordinators ----------")
    add("INSERT IGNORE INTO subject_coordinators (subject_id, coordinator_user_id) VALUES")
    add(",\n".join(f"  ({s}, {c})" for s, c in SUBJECT_COORDINATORS) + ";")
    add("")

    enrolled = enrolments()
    add("-- ---------- Student enrolments ----------")
    add("INSERT IGNORE INTO student_courses (student_user_id, course_id) VALUES")
    add(",\n".join(f"  ({s}, {c})" for s, c in enrolled) + ";")
    add("")

    # ---- question bank
    course_number = {cid: number for cid, _s, number, _c, _n, _g in COURSES}
    course_teacher = {}
    for teacher, course in TEACHER_COURSES:
        course_teacher.setdefault(course, teacher)

    question_id = 101
    question_rows, version_rows, option_rows = [], [], []
    question_index = {}
    for course_id in sorted(QUESTIONS):
        for position, (topic, text, options, correct, difficulty) in enumerate(
                QUESTIONS[course_id], start=1):
            code = f"{course_number[course_id]}{position:03d}"
            author = course_teacher[course_id]
            # The questions table names the column "type" and stores the four
            # options inline; question_versions uses "question_type" and keeps
            # its options in answer_options.
            inline = ", ".join(f"'{sql_text(option)}'" for option in options)
            question_rows.append(
                f"  ({question_id}, '{code}', '{sql_text(text)}', '{topic}', "
                f"'MULTIPLE_CHOICE', '{difficulty}', 'ACTIVE', NULL, {inline}, "
                f"{correct}, {course_id}, {author}, 1, @seed_now, @seed_now)"
            )
            version_rows.append(
                f"  ({question_id}, 1, '{sql_text(text)}', '{topic}', "
                f"'MULTIPLE_CHOICE', '{difficulty}', {correct}, {author}, @seed_now)"
            )
            for number, option in enumerate(options, start=1):
                option_rows.append(
                    f"  ({question_id}, 1, {number}, '{sql_text(option)}')"
                )
            question_index.setdefault(course_id, []).append(question_id)
            question_id += 1

    add("-- ---------- Questions (requirements 33, 34) ----------")
    add("-- question_code = 2 digit course number + 3 digit question number.")
    add("INSERT IGNORE INTO questions")
    add("    (question_id, question_code, content, topic, type, difficulty,")
    add("     status, illustration_path, answer_option_1, answer_option_2,")
    add("     answer_option_3, answer_option_4, correct_option_number, course_id,")
    add("     created_by_user_id, current_version_no, created_at, updated_at) VALUES")
    add(",\n".join(question_rows) + ";")
    add("")

    add("-- ---------- Question versions ----------")
    add("INSERT IGNORE INTO question_versions")
    add("    (question_id, version_no, content, topic, question_type, difficulty,")
    add("     correct_option_number, created_by_user_id, created_at) VALUES")
    add(",\n".join(version_rows) + ";")
    add("")

    add("-- ---------- Answer options ----------")
    add("INSERT IGNORE INTO answer_options")
    add("    (question_id, version_no, option_number, option_text) VALUES")
    add(",\n".join(option_rows) + ";")
    add("")

    # ---- exams
    subject_of_course = {cid: sid for cid, sid, _n, _c, _nm, _g in COURSES}
    subject_number = {sid: number for sid, number, _c, _n in SUBJECTS}
    coordinator_of_subject = dict((s, c) for s, c in SUBJECT_COORDINATORS)

    exam_rows, exam_version_rows, exam_question_rows = [], [], []
    exam_questions = {}
    for exam_id, course_id, author, title, status, exam_number, count in EXAMS:
        sid = subject_of_course[course_id]
        code = f"{subject_number[sid]}{course_number[course_id]}{exam_number:02d}"
        pool = question_index[course_id][:count]
        exam_questions[exam_id] = pool

        current = 1
        exam_rows.append(
            f"  ({exam_id}, '{code}', {course_id}, {author}, {current}, "
            f"@seed_now, @seed_now)"
        )

        reviewer = "NULL"
        reviewed = "NULL"
        submitted = "NULL"
        rejection = "NULL"
        if status in ("PENDING_APPROVAL", "APPROVED", "REJECTED"):
            submitted = "DATE_SUB(@seed_now, INTERVAL 30 DAY)"
        if status in ("APPROVED", "REJECTED"):
            reviewer = str(coordinator_of_subject[sid])
            reviewed = "DATE_SUB(@seed_now, INTERVAL 29 DAY)"
        if status == "REJECTED":
            rejection = ("'Three questions repeat the same idea. "
                         "Please broaden the coverage before resubmitting.'")

        duration = 45 + (count * 3)
        share = round(100.0 / count, 2)
        scores = [share] * count
        scores[-1] = round(100.0 - share * (count - 1), 2)

        exam_version_rows.append(
            f"  ({exam_id}, 1, '{sql_text(title)}', {duration}, 0, "
            f"'Prepared from the course question bank.', "
            f"'Answer every question. Each question has one correct option.', "
            f"100.00, '{status}', {author}, @seed_now, {submitted}, "
            f"{reviewer}, {reviewed}, {rejection})"
        )
        for order, (question, score) in enumerate(zip(pool, scores), start=1):
            exam_question_rows.append(
                f"  ({exam_id}, 1, {order}, {question}, 1, {score:.2f})"
            )

    add("-- ---------- Exams (requirements 38, 39) ----------")
    add("-- exam_code = subject number + course number + exam number.")
    add("INSERT IGNORE INTO exams")
    add("    (exam_id, exam_code, course_id, created_by_user_id, current_version_no,")
    add("     created_at, updated_at) VALUES")
    add(",\n".join(exam_rows) + ";")
    add("")

    add("-- ---------- Exam versions ----------")
    add("-- Statuses cover the whole approval lifecycle: a draft still being")
    add("-- written, one waiting on a coordinator, approved ones ready to")
    add("-- schedule, and one returned with a reason.")
    add("INSERT IGNORE INTO exam_versions")
    add("    (exam_id, version_no, title, duration_minutes, cumulative_extension_minutes,")
    add("     teacher_notes, student_instructions, total_score, status,")
    add("     version_created_by_user_id, created_at, submitted_at,")
    add("     reviewed_by_user_id, reviewed_at, rejection_reason) VALUES")
    add(",\n".join(exam_version_rows) + ";")
    add("")

    add("-- ---------- Exam questions ----------")
    add("INSERT IGNORE INTO exam_version_questions")
    add("    (exam_id, exam_version_no, order_number, question_id,")
    add("     question_version_no, score) VALUES")
    add(",\n".join(exam_question_rows) + ";")
    add("")

    # ---- executions and submissions
    random.seed(11)

    exam_course = {exam_id: course for exam_id, course, *_ in EXAMS}
    execution_rows, submission_rows, answer_rows = [], [], []
    extension_rows, notification_rows = [], []
    submission_id = 501
    answer_id = 5001
    notification_id = 9001

    for (execution_id, exam_id, code, creator, day_offset,
         duration, state) in EXECUTIONS:
        course_id = exam_course[exam_id]
        pool = exam_questions[exam_id]
        share = round(100.0 / len(pool), 2)

        if state == "PAST":
            opening = f"DATE_SUB(@seed_now, INTERVAL {-day_offset} DAY)"
            closing = f"DATE_SUB(@seed_now, INTERVAL {-day_offset - 1} DAY)"
            status = "CLOSED"
            closed = closing
        elif state == "TODAY":
            opening = "DATE_SUB(@seed_now, INTERVAL 30 MINUTE)"
            closing = "DATE_ADD(@seed_now, INTERVAL 3 HOUR)"
            status = "OPEN"
            closed = "NULL"
        else:
            opening = f"DATE_ADD(@seed_now, INTERVAL {day_offset} DAY)"
            closing = f"DATE_ADD(@seed_now, INTERVAL {day_offset} DAY) + INTERVAL 3 HOUR"
            status = "SCHEDULED"
            closed = "NULL"

        class_list = students_in(course_id, enrolled)
        sitting = class_list if state == "PAST" else class_list[:6]

        started = submitted = auto = 0
        scores = []
        for position, student in enumerate(sitting):
            if state == "FUTURE":
                break

            correct = 0
            student_answers = []
            for question in pool:
                # A spread of ability, stable across runs because the seed is fixed.
                right = random.random() < (0.45 + 0.4 * ((position + question) % 5) / 4)
                chosen = 1 if right else 2
                if right:
                    correct += 1
                student_answers.append((question, chosen, right))

            score = round(correct * share, 2)
            score = min(score, 100.00)

            if state == "TODAY" and position >= 3:
                # Three students are still working, so a teacher can watch a
                # submission arrive and the status change while she looks.
                sub_status, submitted_at, actual = "IN_PROGRESS", "NULL", "NULL"
                final = automatic = "NULL"
                reviewed_by = reviewed_at = published_by = published_at = "NULL"
                feedback = reason = "NULL"
                started += 1
            else:
                auto_submitted = state == "PAST" and position % 11 == 10
                sub_status = "AUTO_SUBMITTED" if auto_submitted else "SUBMITTED"
                submitted_at = opening + f" + INTERVAL {duration - 5} MINUTE"
                actual = str(duration - 5)
                automatic = f"{score:.2f}"
                final = automatic
                reviewed_by = reviewed_at = published_by = published_at = "NULL"
                feedback = reason = "NULL"
                started += 1
                if auto_submitted:
                    auto += 1
                else:
                    submitted += 1

                # Execution 84 stays reviewed but unpublished so a teacher opening
                # Grade Review has real work waiting rather than a finished set.
                if state == "PAST" and execution_id != 84:
                    sub_status = "PUBLISHED"
                    reviewed_by = str(creator)
                    reviewed_at = closing
                    published_by = str(creator)
                    published_at = closing
                    feedback = "'Well done. Review the questions you missed.'"
                    if position % 9 == 4:
                        # A hand adjusted grade with the mandatory justification
                        # a student now reads with her result (requirement 39).
                        final = f"{min(score + 5, 100):.2f}"
                        reason = ("'Question 3 was ambiguous, so five marks were "
                                  "returned to every student who attempted it.'")
                    scores.append(float(final))

            starting = opening + " + INTERVAL 2 MINUTE"
            submission_rows.append(
                f"  ({submission_id}, {execution_id}, {student}, {starting}, "
                f"{submitted_at}, '{sub_status}', {duration}, 0, NULL, {actual}, "
                f"{automatic}, {final}, {feedback}, {reason}, {reviewed_by}, "
                f"{reviewed_at}, {published_by}, {published_at})"
            )
            # An attempt still in progress has answers saved but not marked.
            # Grading happens when it is submitted, and the grader refuses a
            # submission whose answers already carry a mark.
            in_progress = sub_status == "IN_PROGRESS"
            for question, chosen, right in student_answers:
                correct_value = "NULL" if in_progress else ("TRUE" if right else "FALSE")
                score_value = (
                    "NULL" if in_progress else f"{share if right else 0:.2f}"
                )
                answer_rows.append(
                    f"  ({answer_id}, {submission_id}, {question}, 1, {chosen}, "
                    f"{correct_value}, {score_value})"
                )
                answer_id += 1

            if state == "PAST":
                notification_rows.append(
                    f"  ({notification_id}, {student}, 'GRADE_PUBLISHED', "
                    f"'Grade published', 'Your result is available.', "
                    f"{exam_id}, {execution_id}, {submission_id}, "
                    f"'grade:{submission_id}', @seed_now, NULL)"
                )
                notification_id += 1

            submission_id += 1

        average = f"{sum(scores) / len(scores):.2f}" if scores else "NULL"
        median = f"{sorted(scores)[len(scores) // 2]:.2f}" if scores else "NULL"

        execution_rows.append(
            f"  ({execution_id}, '{code}', {exam_id}, 1, {opening}, {closing}, "
            f"{duration}, 0, '{status}', {creator}, @seed_now, {closed}, "
            f"{average}, {median}, {started}, {submitted}, {auto})"
        )

    add("-- ---------- Exam executions ----------")
    add("-- Past executions are closed and published, three are open right now so")
    add("-- a demo can show live counters, and three are still scheduled.")
    add("INSERT IGNORE INTO exam_executions")
    add("    (execution_id, execution_code, exam_id, exam_version_no, opening_time,")
    add("     closing_time, duration_minutes, cumulative_extension_minutes, status,")
    add("     created_by_user_id, created_at, closed_at, average_score, median_score,")
    add("     started_count, submitted_count, auto_submitted_count) VALUES")
    add(",\n".join(execution_rows) + ";")
    add("")

    add("-- ---------- Exam submissions ----------")
    add("INSERT IGNORE INTO exam_submissions")
    add("    (submission_id, execution_id, student_user_id, started_at, submitted_at,")
    add("     status, allocated_duration_minutes, extra_minutes, extension_reason,")
    add("     actual_duration_minutes, automatic_score, final_score, teacher_feedback,")
    add("     manual_change_reason, reviewed_by_user_id, reviewed_at,")
    add("     published_by_user_id, published_at) VALUES")
    add(",\n".join(submission_rows) + ";")
    add("")

    add("-- ---------- Student answers ----------")
    add("INSERT IGNORE INTO student_answers")
    add("    (answer_id, submission_id, question_id, question_version_no,")
    add("     selected_option_number, is_correct, score_received) VALUES")
    add(",\n".join(answer_rows) + ";")
    add("")

    if notification_rows:
        add("-- ---------- Notifications ----------")
        add("INSERT IGNORE INTO notifications")
        add("    (notification_id, recipient_user_id, notification_type, title, message,")
        add("     related_exam_id, related_execution_id, related_submission_id,")
        add("     deduplication_key, created_at, read_at) VALUES")
        add(",\n".join(notification_rows) + ";")
        add("")

    # ---- study bots (requirements 42 to 50)
    bot_defs = [
        (301, 21, "Mathematics Grade 10 Study Bot", 1101),
        (302, 24, "Physics Grade 10 Study Bot", 1104),
        (303, 26, "English Grade 10 Study Bot", 1102),
    ]
    add("-- ---------- Course Bots ----------")
    add("INSERT IGNORE INTO course_bots")
    add("    (bot_id, course_id, name, status, created_by_user_id, external_provider,")
    add("     external_bot_id, created_at, updated_at) VALUES")
    add(",\n".join(
        f"  ({bid}, {course}, '{sql_text(name)}', 'ACTIVE', {author}, "
        f"'deterministic', 'bot-{bid}', @seed_now, @seed_now)"
        for bid, course, name, author in bot_defs
    ) + ";")
    add("")

    sources = [
        (401, 301, "FREE_TEXT", "Algebra revision notes", 1101,
         "Linear equations. To solve ax + b = c, subtract b from both sides and "
         "then divide by a. Always apply the same operation to both sides so the "
         "equality is preserved. Example: 3x + 7 = 22 gives 3x = 15 and x = 5."),
        (402, 301, "TXT", "Geometry formula sheet", 1102,
         "Triangles. The interior angles of any triangle sum to 180 degrees. In a "
         "right triangle the square of the hypotenuse equals the sum of the squares "
         "of the other two sides. Circles. Circumference is 2*pi*r and area is pi*r^2."),
        (403, 301, "FREE_TEXT", "Quadratic equations", 1101,
         "A quadratic has the form ax^2 + bx + c = 0. Its roots are given by "
         "x = (-b +/- sqrt(b^2 - 4ac)) / 2a. The expression under the square root is "
         "the discriminant: positive gives two real roots, zero gives one, negative "
         "gives none."),
        (404, 302, "FREE_TEXT", "Mechanics revision notes", 1104,
         "Newton's laws of motion. A body stays at rest or in uniform motion unless "
         "a net force acts on it. Acceleration is proportional to the net force and "
         "inversely proportional to mass, written F = ma. Every action has an equal "
         "and opposite reaction."),
        (405, 302, "DOCX", "Exam technique handout", 1101,
         "Read every question twice before answering. Where a calculation is needed, "
         "write the intermediate steps so a small slip does not cost the whole answer. "
         "Leave hard questions until the end."),
        (406, 303, "FREE_TEXT", "Grammar reference", 1102,
         "The present simple describes habits and general truths, and takes an s in "
         "the third person singular: she goes, he writes. The past simple of an "
         "irregular verb must be learned: write becomes wrote, begin becomes began."),
    ]
    add("-- ---------- Bot knowledge sources (requirements 43, 45) ----------")
    add("-- Several sources are added by a second teacher of the course, which is")
    add("-- what requirement 45 allows.")
    add("INSERT IGNORE INTO bot_sources")
    add("    (source_id, bot_id, source_type, display_name, extracted_text,")
    add("     content_sha256, added_by_user_id, status, created_at,")
    add("     question_id, question_version_no, current_version_no) VALUES")
    source_rows = []
    for sid, bot, kind, name, author, text in sources:
        digest = hashlib.sha256(text.encode()).hexdigest()
        source_rows.append(
            f"  ({sid}, {bot}, '{kind}', '{sql_text(name)}',\n"
            f"   '{sql_text(text)}',\n"
            f"   '{digest}', {author}, 'ACTIVE', @seed_now, NULL, NULL, 1)"
        )
    add(",\n".join(source_rows) + ";")
    add("")

    # A conversation must belong to a student who takes that bot's course, or
    # the bot would never appear for her and the history would be unreachable.
    bot_course = {bot_id: course for bot_id, course, _name, _author in bot_defs}
    conversation_plan = [
        (301, 0, [
            ("How do I solve 3x + 7 = 22?", True),
            ("What is the discriminant used for?", True),
            ("Will this be on the exam?", False),
        ]),
        (301, 1, [
            ("Explain the quadratic formula step by step.", True),
            ("What is the area of a circle with radius 4?", True),
        ]),
        (301, 2, [("Do the angles in a triangle always add to 180?", True)]),
        (302, 0, [
            ("What does F = ma actually mean?", True),
            ("Can you tell me my grade for the midterm?", False),
        ]),
        (302, 1, [("What is Newton's third law?", True)]),
        (303, 0, [
            ("When do I add an s to a verb?", True),
            ("Give me the answers to tomorrow's test.", False),
        ]),
    ]

    conversations = []
    conversation_id = 501
    for bot, position, messages in conversation_plan:
        class_list = students_in(bot_course[bot], enrolled)
        if position >= len(class_list):
            raise SystemExit(
                f"Seed error: course {bot_course[bot]} has fewer than "
                f"{position + 1} students, so bot {bot} cannot have that conversation."
            )
        conversations.append((conversation_id, bot, class_list[position], messages))
        conversation_id += 1
    add("-- ---------- Bot conversations (requirement 49) ----------")
    add("-- A student sees only her own conversation, and the provider subject id")
    add("-- is a pseudonym so the external bot never receives her identity.")
    add("INSERT IGNORE INTO bot_conversations")
    add("    (conversation_id, bot_id, student_user_id, provider_subject_id,")
    add("     created_at, updated_at) VALUES")
    conv_rows, message_rows = [], []
    message_id = 601
    for index, (conv, bot, student, messages) in enumerate(conversations, start=1):
        subject = str(uuid.UUID(int=random.getrandbits(128), version=4))
        days = 20 - index
        conv_rows.append(
            f"  ({conv}, {bot}, {student}, '{subject}',\n"
            f"   DATE_SUB(@seed_now, INTERVAL {days} DAY),"
            f" DATE_SUB(@seed_now, INTERVAL {days - 1} DAY))"
        )
        for sequence, (question, answered) in enumerate(messages, start=1):
            answer = (
                "Answered from the course material."
                if answered else
                "I could not find an answer to that in this course's materials. "
                "Try asking about a topic the course covers, or ask your teacher."
            )
            status = "ANSWERED" if answered else "NO_SUITABLE_ANSWER"
            message_rows.append(
                f"  ({message_id}, {conv}, {sequence}, '{sql_text(question)}',\n"
                f"   '{sql_text(question.lower().rstrip(chr(63)))}',\n"
                f"   '{sql_text(answer)}', '{status}', 'seed-{message_id}',\n"
                f"   DATE_SUB(@seed_now, INTERVAL {days * 24 - sequence} HOUR))"
            )
            message_id += 1
    add(",\n".join(conv_rows) + ";")
    add("")
    add("-- ---------- Bot messages (requirements 48, 49) ----------")
    add("-- Questions the bot could not answer are stored as NO_SUITABLE_ANSWER,")
    add("-- which is the case requirement 48 asks the interface to report.")
    add("INSERT IGNORE INTO bot_messages")
    add("    (message_id, conversation_id, sequence_no, question_text,")
    add("     normalized_question, answer_text, answer_status, provider_request_id,")
    add("     created_at) VALUES")
    add(",\n".join(message_rows) + ";")
    add("")

    add("-- ---------- Verification ----------")
    add("SELECT 'subjects' AS entity, COUNT(*) AS rows_present FROM subjects")
    for table in ["courses", "users", "teacher_courses", "student_courses",
                  "questions", "exams", "exam_executions", "exam_submissions",
                  "student_answers", "course_bots", "bot_sources",
                  "bot_conversations", "bot_messages", "notifications"]:
        add(f"UNION ALL SELECT '{table}', COUNT(*) FROM {table}")
    add(";")
    add("")
    return out


def check_against_schema(sql_text_out, schema_path):
    """Fails loudly if an insert names a column the schema does not declare.

    A mismatch here is only discovered when the seed is loaded, which is late
    and easy to misread as a data problem rather than a column-name one.
    """
    import re as _re

    schema = open(schema_path, encoding="utf-8").read()
    declared = {}
    for match in _re.finditer(
            r"CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\n\) ENGINE", schema, _re.S):
        columns = []
        for line in match.group(2).splitlines():
            line = line.strip()
            if not line or line.startswith((
                    "--", "CONSTRAINT", "PRIMARY", "KEY", "UNIQUE",
                    "FOREIGN", "CHECK", "REFERENCES", ")")):
                continue
            token = line.split()[0]
            if token.isidentifier():
                columns.append(token)
        declared[match.group(1)] = columns

    problems = []
    for match in _re.finditer(
            r"INSERT IGNORE INTO (\w+)\s*\n?\s*\((.*?)\)\s*VALUES",
            sql_text_out, _re.S):
        table = match.group(1)
        used = [
            column.strip()
            for column in _re.sub(r"--.*", "", match.group(2)).replace("\n", " ").split(",")
            if column.strip()
        ]
        unknown = [c for c in used if c not in declared.get(table, [])]
        if unknown:
            problems.append(f"{table}: {unknown}")

    if problems:
        raise SystemExit("Seed does not match the schema:\n  " + "\n  ".join(problems))


if __name__ == "__main__":
    lines = build()
    target = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "database", "seed_demo_data.sql",
    )
    body = "\n".join(lines) + "\n"
    check_against_schema(
        body,
        os.path.join(os.path.dirname(target), "init.sql"),
    )
    with open(target, "w", encoding="utf-8") as handle:
        handle.write(body)
    print(f"wrote {target} ({len(lines)} statements/blocks)")
