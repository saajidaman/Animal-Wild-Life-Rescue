package org.wildlife;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import org.wildlife.Model.*;
import org.wildlife.Service.*;
import org.wildlife.Storage.*;
import static org.wildlife.Model.Types.*;

/** Dependency-free behavioural tests. Failures produce a nonzero process exit code. */
public final class CaseTest {
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static int passed;
    private static final List<Path> TEMPORARIES = new ArrayList<>();
    private CaseTest() { }
    private static final class MemoryRepository implements StateStorage {
        private State state = State.empty(); private boolean fail;
        @Override public State load() { return state; }
        @Override public void save(State next) throws IOException {
            if (fail) throw new IOException("Injected disk failure"); state = next;
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void test(String label, Checked action) throws Exception {
        action.run(); passed++; System.out.printf("PASS T%02d %s%n", passed, label);
    }
    private static void expect(Class<? extends Throwable> type, Checked action) throws Exception {
        try { action.run(); } catch (Exception e) { if (type.isInstance(e)) return; throw e; }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    private static ReportRequest request(AnimalType type, Condition condition, Risk risk, int count, boolean danger) {
        return new ReportRequest(type, condition, new Location("Park, near \"Gate A\"", 3, 4, risk), count, danger,
                Set.of(), Set.of(), new Reporter("Example Reporter", "example@example.invalid"));
    }
    private static ReportRequest normal() { return request(AnimalType.BIRD, Condition.DISPLACED, Risk.LOW, 1, false); }
    private static RescueTeam team(String id, int capacity, boolean operational) {
        return new RescueTeam(id, "Team " + id, List.of("Example A", "Example B"), Set.of(AnimalType.BIRD),
                Set.of(Skill.BIRD_HANDLING, Skill.CLIMBING), Set.of(Equipment.CARRIER, Equipment.NET, Equipment.CLIMBING_KIT),
                new Location("Base", 0, 0, Risk.LOW), operational, capacity);
    }
    private static IncidentManager manager() throws IOException { return new IncidentManager(new MemoryRepository()); }
    private static Path temporary() throws IOException { Path p = Files.createTempDirectory("wildlife-tests-"); TEMPORARIES.add(p); return p; }
    private static void bootstrap(Path path, State state) throws IOException {
        Files.writeString(path.resolve("teams.csv"), CsvFileManager.teamsCsv(state));
        Files.writeString(path.resolve("incidents.csv"), CsvFileManager.incidentsCsv(state));
    }
    private static State sample() {
        RescueIncident incident = new RescueIncident("INC-ONE", normal(), Instant.parse("2026-10-22T00:00:00Z"), 15,
                IncidentStatus.PENDING_ASSIGNMENT, "");
        return new State(Map.of(incident.id(), incident), Map.of("TEAM", team("TEAM", 1, true)));
    }
    private static void corruptIncident(int column, String value) throws Exception {
        Path p = temporary(); bootstrap(p, sample());
        List<List<String>> rows = new ArrayList<>(Csv.parse(Files.readString(p.resolve("incidents.csv"))));
        List<String> row = new ArrayList<>(rows.get(1)); row.set(column, value); rows.set(1, row);
        Files.writeString(p.resolve("incidents.csv"), Csv.encode(rows));
        expect(IOException.class, () -> new CsvFileManager(p).load());
    }
    private static void runTests() throws Exception {
        test("Priority threshold boundaries", () -> {
            int[] scores = {0,24,25,49,50,79,80,100};
            Priority[] expected = {Priority.LOW,Priority.LOW,Priority.MEDIUM,Priority.MEDIUM,Priority.HIGH,Priority.HIGH,Priority.CRITICAL,Priority.CRITICAL};
            for (int i=0;i<scores.length;i++) check(Priority.fromScore(scores[i]) == expected[i], "boundary " + scores[i]);
            expect(ValidationException.class, () -> Priority.fromScore(101));
        });
        test("Priority weights, immediate danger and score cap", () -> {
            PriorityCalculator calculator = new PriorityCalculator();
            check(calculator.calculate(normal()) == 15, "low baseline");
            check(calculator.calculate(request(AnimalType.MAMMAL,Condition.ABANDONED,Risk.MEDIUM,2,false)) == 40, "weight sum");
            check(calculator.calculate(request(AnimalType.BIRD,Condition.DISPLACED,Risk.LOW,1,true)) == 80, "danger must be critical");
            check(calculator.calculate(request(AnimalType.BIRD,Condition.INJURED,Risk.HIGH,10000,true)) == 100, "bounded");
        });
        test("Animal subtype polymorphism", () -> {
            Animal[] animals = {new Bird(),new Reptile(),new Mammal()};
            check(animals[0].requiredSkills().contains(Skill.BIRD_HANDLING), "bird");
            check(animals[1].requiredEquipment().contains(Equipment.GLOVES), "reptile");
            check(animals[2].requiredSkills().contains(Skill.MAMMAL_HANDLING), "mammal");
        });
        test("Blank location and invalid coordinates rejected", () -> {
            expect(ValidationException.class, () -> new Location(" ",0,0,Risk.LOW));
            expect(ValidationException.class, () -> new Location("Park",Double.NaN,0,Risk.LOW));
            expect(ValidationException.class, () -> new Location("Park",-1,0,Risk.LOW));
        });
        test("Invalid counts, missing contact and control characters rejected", () -> {
            expect(ValidationException.class, () -> request(AnimalType.BIRD,Condition.INJURED,Risk.LOW,0,false));
            expect(ValidationException.class, () -> new Reporter("Person", ""));
            expect(ValidationException.class, () -> new Reporter("Person\nSecond", "contact"));
        });
        test("Invalid team capacity, members and handling skill rejected", () -> {
            expect(ValidationException.class, () -> team("BAD",0,true));
            expect(ValidationException.class, () -> new RescueTeam("X","Team",List.of(),Set.of(AnimalType.BIRD),Set.of(Skill.BIRD_HANDLING),Set.of(Equipment.NET),new Location("Base",0,0,Risk.LOW),true,1));
            expect(ValidationException.class, () -> new RescueTeam("X","Team",List.of("A"),Set.of(AnimalType.REPTILE),Set.of(Skill.BIRD_HANDLING),Set.of(Equipment.NET),new Location("Base",0,0,Risk.LOW),true,1));
        });
        test("Report receives unique ID, requirements and pending state", () -> {
            IncidentManager m=manager(); RescueIncident a=m.report(normal()), b=m.report(normal());
            check(!a.id().equals(b.id()),"unique"); check(a.status()==IncidentStatus.PENDING_ASSIGNMENT,"pending");
            check(a.requiredEquipment().containsAll(Set.of(Equipment.CARRIER,Equipment.NET)),"baseline");
        });
        test("Incident listing orders critical before lower priorities", () -> {
            IncidentManager m=manager(); m.report(normal());
            RescueIncident urgent=m.report(request(AnimalType.BIRD,Condition.INJURED,Risk.HIGH,1,true));
            check(m.incidents().get(0).id().equals(urgent.id()),"urgent first");
        });
        test("Duplicate team ID rejected without replacement", () -> {
            IncidentManager m=manager(); m.register(team("A",1,true));
            expect(ValidationException.class, () -> m.register(team("A",5,true)));
            check(m.snapshot().teams().get("A").maxIncidents()==1,"original unchanged");
        });
        test("Animal compatibility filters teams", () -> {
            IncidentManager m=manager();m.register(team("A",1,true));
            RescueIncident i=m.report(request(AnimalType.REPTILE,Condition.INJURED,Risk.LOW,1,false));
            check(m.matches(i.id()).isEmpty(),"no compatible team");
        });
        test("Missing special skills/equipment excludes teams", () -> {
            IncidentManager m=manager();m.register(team("A",1,true));
            ReportRequest r=normal();
            RescueIncident i=m.report(new ReportRequest(r.animalType(),r.condition(),r.location(),1,false,Set.of(Skill.WATER_RESCUE),Set.of(Equipment.BOAT),r.reporter()));
            check(m.matches(i.id()).isEmpty(),"water rescue exclusion");
            expect(ValidationException.class, () -> m.assign(i.id(),"A"));
        });
        test("Assignment independently rejects missing equipment", () -> {
            IncidentManager m=manager();
            m.register(new RescueTeam("A","No net",List.of("A"),Set.of(AnimalType.BIRD),Set.of(Skill.BIRD_HANDLING),Set.of(Equipment.CARRIER),new Location("Base",0,0,Risk.LOW),true,1));
            RescueIncident i=m.report(normal()); expect(ValidationException.class, () -> m.assign(i.id(),"A"));
            check(m.incident(i.id()).status()==IncidentStatus.PENDING_ASSIGNMENT,"unchanged");
        });
        test("Unavailable team excluded and direct assignment rejected", () -> {
            IncidentManager m=manager();m.register(team("A",1,false));RescueIncident i=m.report(normal());
            check(m.matches(i.id()).isEmpty(),"excluded");expect(ValidationException.class, () -> m.assign(i.id(),"A"));
        });
        test("Matching ranks utilization before distance, then ID", () -> {
            IncidentManager m=manager();m.register(team("A",2,true));m.register(team("B",2,true));
            RescueIncident i=m.report(normal());m.assign(i.id(),"A");RescueIncident next=m.report(normal());
            check(m.matches(next.id()).get(0).team().id().equals("B"),"idle first");
            IncidentManager fresh=manager();fresh.register(team("B",1,true));fresh.register(team("A",1,true));
            check(fresh.matches(fresh.report(normal()).id()).get(0).team().id().equals("A"),"stable tie");
        });
        test("Distance breaks equal-utilization ties", () -> {
            IncidentManager m=manager();m.register(team("A",1,true));
            RescueTeam near=team("Z",1,true);
            m.register(new RescueTeam(near.id(),near.name(),near.members(),near.animalTypes(),near.skills(),near.equipment(),new Location("Near",3,4,Risk.LOW),true,1));
            check(m.matches(m.report(normal()).id()).get(0).team().id().equals("Z"),"nearest first");
        });
        test("Assigned team accepts work until configured capacity", () -> {
            IncidentManager m=manager();m.register(team("A",2,true));RescueIncident a=m.report(normal());m.assign(a.id(),"A");
            RescueIncident b=m.report(normal());check(m.matches(b.id()).size()==1,"spare slot remains");m.assign(b.id(),"A");
            RescueIncident c=m.report(normal());expect(ValidationException.class, () -> m.assign(c.id(),"A"));
            check(m.snapshot().workload("A")==2,"capacity respected");
        });
        //
        test("Higher-ranked compatible pending report blocks lower assignment", () -> {
            IncidentManager m=manager();m.register(team("A",2,true));RescueIncident low=m.report(normal());
            m.report(request(AnimalType.BIRD,Condition.INJURED,Risk.HIGH,1,true));
            expect(ValidationException.class, () -> m.assign(low.id(),"A"));
        });
        test("Incompatible critical report does not block available specialist", () -> {
            IncidentManager m=manager();m.register(team("A",1,true));RescueIncident low=m.report(normal());
            m.report(request(AnimalType.REPTILE,Condition.INJURED,Risk.HIGH,1,true));m.assign(low.id(),"A");
            check(m.snapshot().workload("A")==1,"use specialist capacity");
        });
        test("No match leaves incident pending", () -> {
            IncidentManager m=manager();RescueIncident i=m.report(normal());check(m.matches(i.id()).isEmpty(),"empty");
            check(m.incident(i.id()).status()==IncidentStatus.PENDING_ASSIGNMENT,"pending retained");
        });
        test("Valid lifecycle completes and releases capacity", () -> {
            IncidentManager m=manager();m.register(team("A",1,true));RescueIncident i=m.report(normal());m.assign(i.id(),"A");
            m.updateStatus(i.id(),IncidentStatus.IN_PROGRESS);m.updateStatus(i.id(),IncidentStatus.COMPLETED);
            check(m.snapshot().workload("A")==0,"released");
            check(m.snapshot().teams().get("A").status(0)==TeamStatus.AVAILABLE,"available");
        });
        test("Invalid status skips, duplicate assignment and reopen rejected", () -> {
            IncidentManager m=manager();m.register(team("A",1,true));RescueIncident i=m.report(normal());
            expect(ValidationException.class, () -> m.updateStatus(i.id(),IncidentStatus.COMPLETED));m.assign(i.id(),"A");
            expect(ValidationException.class, () -> m.assign(i.id(),"A"));
            expect(ValidationException.class, () -> m.updateStatus(i.id(),IncidentStatus.COMPLETED));
            m.updateStatus(i.id(),IncidentStatus.IN_PROGRESS);m.updateStatus(i.id(),IncidentStatus.COMPLETED);
            expect(ValidationException.class, () -> m.updateStatus(i.id(),IncidentStatus.IN_PROGRESS));
        });
        test("Team cannot become unavailable while active", () -> {
            IncidentManager m=manager();m.register(team("A",1,true));m.assign(m.report(normal()).id(),"A");
            expect(ValidationException.class, () -> m.setOperational("A",false));
        });
        test("Idle team availability can be changed", () -> {
            IncidentManager m=manager();m.register(team("A",1,true));m.setOperational("A",false);
            check(m.snapshot().teams().get("A").status(0)==TeamStatus.UNAVAILABLE,"disabled");m.setOperational("A",true);
            check(m.snapshot().teams().get("A").operational(),"enabled");
        });
        test("CSV preserves commas, quotes, multiline fields, BOM and CRLF", () -> {
            List<List<String>> rows=List.of(List.of("a,b","quote \"x\"","line\nsecond",""));
            check(Csv.parse(Csv.encode(rows)).equals(rows),"roundtrip");
            check(Csv.parse("\uFEFFa,b\r\nc,d\r\n").size()==2,"BOM CRLF");
            check(Csv.parse("a,").get(0).equals(List.of("a","")),"trailing empty");
        });

    }

}

