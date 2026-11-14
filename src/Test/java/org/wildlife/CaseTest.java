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
        test("Malformed CSV quotes rejected", () -> {
            expect(IOException.class, () -> Csv.parse("\"open"));
            expect(IOException.class, () -> Csv.parse("\"closed\"x,other"));
            expect(IOException.class, () -> Csv.parse("bad\"quote"));
        });
        test("CSV bootstrap and snapshot restart preserve complete data", () -> {
            Path p=temporary();bootstrap(p,sample());CsvFileManager repo=new CsvFileManager(p);State a=repo.load();repo.save(a);
            State b=new CsvFileManager(p).load();check(CsvFileManager.incidentsCsv(a).equals(CsvFileManager.incidentsCsv(b)),"incidents");
            check(CsvFileManager.teamsCsv(a).equals(CsvFileManager.teamsCsv(b)),"teams");
        });
        test("Duplicate incident IDs rejected on load", () -> {
            Path p=temporary();bootstrap(p,sample());List<List<String>> rows=new ArrayList<>(Csv.parse(Files.readString(p.resolve("incidents.csv"))));
            rows.add(rows.get(1));Files.writeString(p.resolve("incidents.csv"),Csv.encode(rows));expect(IOException.class, () -> new CsvFileManager(p).load());
        });
        test("Invalid CSV animal type rejected", () -> corruptIncident(1,"DRAGON"));
        test("Invalid CSV priority score and category rejected", () -> {corruptIncident(14,"99");corruptIncident(15,"CRITICAL");});
        test("Invalid CSV location and boolean rejected", () -> {corruptIncident(3,"");corruptIncident(8,"maybe");});
        test("Wrong CSV header/field counts and missing file rejected", () -> {
            Path p=temporary();bootstrap(p,sample());Files.writeString(p.resolve("teams.csv"),"wrong,header\n");expect(IOException.class, () -> new CsvFileManager(p).load());
            bootstrap(p,sample());Files.writeString(p.resolve("incidents.csv"),Csv.encode(List.of(CsvFileManager.INCIDENT_HEADER,List.of("short"))));expect(IOException.class, () -> new CsvFileManager(p).load());
            Files.delete(p.resolve("incidents.csv"));expect(IOException.class, () -> new CsvFileManager(p).load());
        });
        test("Unknown assigned team and inconsistent team status rejected", () -> {
            Path p=temporary();State s=sample();RescueIncident assigned=s.incidents().get("INC-ONE").assign("UNKNOWN");
            bootstrap(p,new State(Map.of(assigned.id(),assigned),s.teams()));expect(IOException.class, () -> new CsvFileManager(p).load());
            bootstrap(p,s);String text=Files.readString(p.resolve("teams.csv")).replace("AVAILABLE","ASSIGNED");Files.writeString(p.resolve("teams.csv"),text);
            expect(IOException.class, () -> new CsvFileManager(p).load());
        });
        test("Snapshot pointer traversal and missing generation rejected", () -> {
            Path p=temporary();Files.writeString(p.resolve("CURRENT"),"../../elsewhere");expect(IOException.class, () -> new CsvFileManager(p).load());
            Files.writeString(p.resolve("CURRENT"),"snapshot-00000000-0000-0000-0000-000000000000");expect(IOException.class, () -> new CsvFileManager(p).load());
        });
        test("Failed report save leaves memory unchanged", () -> {
            MemoryRepository r=new MemoryRepository();IncidentManager m=new IncidentManager(r);r.fail=true;
            expect(IOException.class, () -> m.report(normal()));check(m.incidents().isEmpty(),"rollback");
        });
        test("Failed assignment/status save leaves assignment and capacity unchanged", () -> {
            MemoryRepository r=new MemoryRepository();IncidentManager m=new IncidentManager(r);m.register(team("A",1,true));RescueIncident i=m.report(normal());r.fail=true;
            expect(IOException.class, () -> m.assign(i.id(),"A"));check(m.snapshot().workload("A")==0,"assignment rollback");
            r.fail=false;m.assign(i.id(),"A");m.updateStatus(i.id(),IncidentStatus.IN_PROGRESS);r.fail=true;
            expect(IOException.class, () -> m.updateStatus(i.id(),IncidentStatus.COMPLETED));check(m.snapshot().workload("A")==1,"completion rollback");
        });
        test("Real filesystem write failure preserves bootstrap and memory", () -> {
            Path p=temporary();bootstrap(p,sample());Files.writeString(p.resolve("snapshots"),"block directory creation");
            IncidentManager m=new IncidentManager(new CsvFileManager(p));expect(IOException.class, () -> m.report(normal()));
            check(m.incidents().size()==1,"memory");check(new CsvFileManager(p).load().incidents().size()==1,"disk");
        });
        test("Multiple commits publish complete current generation", () -> {
            Path p=temporary();IncidentManager m=new IncidentManager(new CsvFileManager(p));m.register(team("A",1,true));
            String old=Files.readString(p.resolve("CURRENT"));RescueIncident i=m.report(normal());m.assign(i.id(),"A");
            check(!old.equals(Files.readString(p.resolve("CURRENT"))),"generation changed");State loaded=new CsvFileManager(p).load();
            check(loaded.workload("A")==1 && loaded.teams().get("A").status(1)==TeamStatus.ASSIGNED,"coherent pair");
        });
        test("Parallel searches equal sequential snapshot results and use workers", () -> {
            IncidentManager m=manager();m.register(team("A",2,true));for(int i=0;i<12;i++)m.report(normal());
            List<IncidentManager.SearchResult> results=m.searchPendingConcurrently();Set<String> threads=new HashSet<>();
            for(var result:results){threads.add(result.workerName());check(result.matches().equals(m.matches(result.incidentId())),"same matches");}
            check(results.size()==12 && threads.size()>1,"multiple workers");check(m.snapshot().workload("A")==0,"search is read-only");
        });
        test("Concurrent report submissions do not lose IDs or records", () -> {
            IncidentManager m=manager();ExecutorService pool=Executors.newFixedThreadPool(4);
            try {List<Callable<String>> jobs=new ArrayList<>();for(int i=0;i<100;i++)jobs.add(()->m.report(normal()).id());
                Set<String> ids=new HashSet<>();for(Future<String> f:pool.invokeAll(jobs))ids.add(f.get());
                check(ids.size()==100 && m.incidents().size()==100,"all unique and retained");
            }finally{pool.shutdownNow();}
        });
        test("Concurrent assignments cannot exceed the final available slot", () -> {
            IncidentManager m=manager();m.register(team("A",1,true));RescueIncident a=m.report(normal()),b=m.report(normal());
            CountDownLatch start=new CountDownLatch(1);AtomicInteger successes=new AtomicInteger();ExecutorService pool=Executors.newFixedThreadPool(2);
            try {List<Future<?>> futures=new ArrayList<>();for(RescueIncident i:List.of(a,b))futures.add(pool.submit(()->{
                try{start.await();m.assign(i.id(),"A");successes.incrementAndGet();}catch(ValidationException expected){return null;}return null;}));
                start.countDown();for(Future<?> f:futures)f.get();check(successes.get()==1 && m.snapshot().workload("A")==1,"one winner");
            }finally{pool.shutdownNow();}
        });
        test("Concurrent persistent reports survive restart without loss", () -> {
            Path p=temporary();IncidentManager m=new IncidentManager(new CsvFileManager(p));ExecutorService pool=Executors.newFixedThreadPool(4);
            try{List<Callable<String>> tasks=new ArrayList<>();for(int i=0;i<12;i++)tasks.add(()->m.report(normal()).id());
                for(Future<String> f:pool.invokeAll(tasks))f.get();check(new CsvFileManager(p).load().incidents().size()==12,"disk retained all");
            }finally{pool.shutdownNow();}
        });
        test("Completed/pending report exports correct groups without contacts", () -> {
            IncidentManager m=manager();m.register(team("A",1,true));RescueIncident i=m.report(normal());m.assign(i.id(),"A");
            m.updateStatus(i.id(),IncidentStatus.IN_PROGRESS);m.updateStatus(i.id(),IncidentStatus.COMPLETED);m.report(normal());
            Path p=CsvFileManager.exportReport(m.snapshot(),temporary());String text=Files.readString(p);List<List<String>> rows=Csv.parse(text);
            check(rows.size()==3,"two rows");Set<String> groups=Set.of(rows.get(1).get(0),rows.get(2).get(0));
            check(groups.containsAll(Set.of("COMPLETED","PENDING_ASSIGNMENT")),"groups");check(!text.contains("example@example.invalid"),"no contact exposure");
        });

    }

}

