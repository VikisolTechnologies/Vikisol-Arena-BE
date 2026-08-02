package com.vikisol.arena.seed;

import com.vikisol.arena.profile.entity.Industry;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Static data pools mirroring the flavor of arena-web's src/lib/mock/seed.ts (Indian names,
 * cities, companies, industries beyond just tech) so seeded/demo data looks realistic without a
 * real content-authoring pass. Shared by SeedDataFactory (single blank profiles on real signup)
 * and DataSeeder (bulk demo data on startup).
 */
public final class IndianData {

    private IndianData() {
    }

    // Seeded once per process so a given `mvnw spring-boot:run` produces a stable-looking demo
    // dataset instead of reshuffling on every restart - same rationale as the frontend's own
    // mulberry32 PRNG in mock/seed.ts.
    public static final Random RANDOM = new Random(42);

    public static final List<String> FIRST_NAMES = List.of(
            "Aarav", "Vivaan", "Aditi", "Ananya", "Rohan", "Priya", "Karthik", "Sneha",
            "Arjun", "Meera", "Ishaan", "Kavya", "Rahul", "Divya", "Aman", "Neha",
            "Siddharth", "Pooja", "Vikram", "Riya", "Sanjay", "Anjali", "Rajesh", "Shreya",
            "Nikhil", "Tanvi", "Varun", "Isha", "Karan", "Aisha", "Manoj", "Nisha");

    public static final List<String> LAST_NAMES = List.of(
            "Sharma", "Verma", "Reddy", "Iyer", "Nair", "Gupta", "Rao", "Khan",
            "Mehta", "Joshi", "Kumar", "Pillai", "Chatterjee", "Desai", "Kapoor", "Menon");

    public static final List<String> LOCATIONS = List.of(
            "Hyderabad", "Bengaluru", "Mumbai", "Pune", "Chennai", "Remote");

    public static final Map<Industry, List<String>> SKILLS_BY_INDUSTRY = Map.of(
            Industry.ENGINEERING, List.of("React", "TypeScript", "Node.js", "Java", "Spring Boot", "AWS", "Docker", "Kubernetes", "SQL", "Python", "Go", "System Design"),
            Industry.DESIGN, List.of("Figma", "UI Design", "UX Research", "Design Systems", "Prototyping", "Motion Design", "Branding", "Illustration"),
            Industry.SALES, List.of("B2B Sales", "Lead Generation", "CRM", "Negotiation", "Account Management", "SaaS Sales", "Cold Outreach"),
            Industry.HEALTHCARE, List.of("Patient Care", "Clinical Research", "Nursing", "Diagnostics", "Telemedicine", "Medical Coding", "EHR Systems"),
            Industry.LOGISTICS, List.of("Supply Chain", "Fleet Management", "Warehouse Ops", "Inventory Planning", "Route Optimization", "Procurement"));

    public static final Map<Industry, List<String>> TITLES_BY_INDUSTRY = Map.of(
            Industry.ENGINEERING, List.of("Software Engineer", "Backend Developer", "Frontend Developer", "DevOps Engineer", "Full Stack Developer", "Data Engineer"),
            Industry.DESIGN, List.of("Product Designer", "UI/UX Designer", "Visual Designer", "Design Lead"),
            Industry.SALES, List.of("Sales Executive", "Account Executive", "Business Development Manager", "Sales Manager"),
            Industry.HEALTHCARE, List.of("Registered Nurse", "Clinical Coordinator", "Healthcare Analyst", "Medical Officer"),
            Industry.LOGISTICS, List.of("Logistics Coordinator", "Supply Chain Analyst", "Warehouse Manager", "Fleet Supervisor"));

    public record CompanySeed(String name, String emoji) {
    }

    public static final List<CompanySeed> COMPANIES = List.of(
            new CompanySeed("Techolution", "🟢"),
            new CompanySeed("Swiggy", "🟠"),
            new CompanySeed("Microsoft", "🔷"),
            new CompanySeed("Innova Solutions", "🔵"),
            new CompanySeed("Paytm", "🟦"),
            new CompanySeed("Zoho", "🟥"),
            new CompanySeed("Freshworks", "🟩"),
            new CompanySeed("Practo", "🩺"),
            new CompanySeed("Delhivery", "📦"),
            new CompanySeed("Razorpay", "⚡"));

    public static final List<String> AVATAR_EMOJIS = List.of(
            "🧑🏽", "👩🏽", "🧔🏽", "👨🏻",
            "👩🏻", "👨🏾", "👩🏾", "🧑🏻",
            "👨🏽", "👩🏼");

    public static <T> T pick(List<T> list) {
        return list.get(RANDOM.nextInt(list.size()));
    }

    public static <T> List<T> pickN(List<T> list, int n) {
        List<T> pool = new java.util.ArrayList<>(list);
        java.util.Collections.shuffle(pool, RANDOM);
        return pool.subList(0, Math.min(n, pool.size()));
    }

    public static int intBetween(int min, int max) {
        return min + RANDOM.nextInt(max - min + 1);
    }

    public static String fullName() {
        return pick(FIRST_NAMES) + " " + pick(LAST_NAMES);
    }

    public static List<Industry> INDUSTRIES_LIST() {
        return List.of(Industry.values());
    }
}
