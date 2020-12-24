/* On my honor, I have neither given nor received unauthorized aid on this assignment */

import java.io.*;
import java.util.*;

enum Opcode {
    J, JR, BEQ, BLTZ, BGTZ, BREAK, SW, LW, SLL, SRL, SRA, NOP, // category 1
    ADD, SUB,MUL,AND,OR,XOR,NOR,SLT,ADDI,ANDI,ORI,XORI; // category 2

    public static final EnumSet<Opcode> CATEGORY2_IMM = EnumSet.of (ADDI, ANDI, ORI, XORI);
    public static final EnumSet<Opcode> BRANCHES = EnumSet.of (J, JR, BEQ, BLTZ, BGTZ);
    public static final EnumSet<Opcode> JUMPS = EnumSet.of (J,JR);
};


class MIPSsim {
    // Parameters
    private static final int START = 256;
    public static int pc = START;
    private static int addr_end;
    private static int cycle = 1;
    private static int mem_start, mem_end;


    // Map of instructions and address
    public static HashMap<Integer, Instr> instrMap = new HashMap<Integer, Instr>();

    // Registers
    public static int[] regs = new int[32];
    public static Set<Integer> toBeRead = new HashSet<>();
    public static Set<Integer> toBeWritten = new HashSet<>();

    // Memory
    public static HashMap<Integer, Integer> mem_map = new HashMap<Integer, Integer>();

    // Functional Units
    private static IF myIF  = IF.getIF();
    private static ISSUE myISSUE =  ISSUE.getISSUE();
    private static ALU1 myALU1 = ALU1.getALU1();
    private static ALU2 myALU2 = ALU2.getALU2();
    private static MEM myMEM = MEM.getMEM();
    private static WB myWB = WB.getWB();

    // Queues
    public static LinkedList<Instr> preIssue   = new LinkedList<Instr>();
    public static LinkedList<Instr> preALU1   = new LinkedList<Instr>();
    public static LinkedList<Instr> preALU2   = new LinkedList<Instr>();
    public static LinkedList<Instr> postALU2 = new LinkedList<Instr>();
    public static LinkedList<Instr> preMEM    = new LinkedList<Instr>();
    public static LinkedList<Instr> postMEM  = new LinkedList<Instr>();


    private static String getRegs(){
        String res = "Registers\n";
        for(int i = 0; i < 32; i ++) {
            if(i % 8 == 0) res += String.format("R%02d:", i);
            res += "\t" + Integer.toString(regs[i]);
            if(i % 8 == 7) res += "\n";
        }
        return res + "\n";
    }

    private static String getData(){
        String res = "Data\n";
        for(int i = mem_start; i <= mem_end; i += 4) {
            if(((i - mem_start)/4) % 8 == 0) res += String.format("%d:", i);
            res += "\t" + Integer.toString(mem_map.get(i));
            if(((i - mem_start)/4) % 8 == 7) res += "\n";
        }
        return res ;
    }

    private static String getPreIssue() {
          String res = "Pre-Issue Queue:\n";
          for(int i = 0; i < 4; i ++) {
              if(i < preIssue.size()) {
                  res +=  "\tEntry " + Integer.toString(i) + ": " + preIssue.get(i).output + "\n";
              } else {
                  res += "\tEntry " + Integer.toString(i) + ":\n";
              }
          }
          return res;
    }

    private static String getPreALU1() {
          String res = "Pre-ALU1 Queue:\n";
          for(int i = 0; i < 2; i ++) {
              if(i < preALU1.size()) {
                  res +=  "\tEntry " + Integer.toString(i) + ": " + preALU1.get(i).output + "\n";
              } else {
                  res += "\tEntry " + Integer.toString(i) + ":\n";
              }
          }
          return res;
    }

    private static String getPreALU2() {
          String res = "Pre-ALU2 Queue:\n";
          for(int i = 0; i < 2; i ++) {
              if(i < preALU2.size()) {
                  res +=  "\tEntry " + Integer.toString(i)  + ": " + preALU2.get(i).output + "\n";
              } else {
                  res += "\tEntry " + Integer.toString(i) + ":\n";
              }
          }
          return res;
    }

    private static String getPostALU2() {
        if(!postALU2.isEmpty()) {
              return  "Post-ALU2 Queue: " + postALU2.get(0).output + "\n";
          } else {
              return  "Post-ALU2 Queue:\n";
          }
    }

    private static String getPreMEM() {
        if(!preMEM.isEmpty()) {
              return  "Pre-MEM Queue: " + preMEM.get(0).output + "\n";
          } else {
              return  "Pre-MEM Queue:\n";
          }
    }

    private static String getPostMEM() {
        if(!postMEM.isEmpty()) {
              return  "Post-MEM Queue: " + postMEM.get(0).output + "\n";
          } else {
              return  "Post-MEM Queue:\n";
          }
    }

    private static void disassemble(String fileName) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            //BufferedWriter bw = new BufferedWriter(new FileWriter("disassembly.txt"));
            String instruction;
            boolean foundBreak = false;
            int temp_addr = 256;
            while((instruction = br.readLine()) != null) {
                if (foundBreak) {
                    int num = Instr.getSignedNum(instruction);
                    mem_map.put(temp_addr, num);
                    mem_end = temp_addr;
                    //bw.write(instruction + "\t" + temp_addr + "\t" + Integer.toString(num) + "\n");
                    //System.out.print(instruction + "\t" + temp_addr + "\t" + Integer.toString(num) + "\n");
                } else {
                    Instr temp = new Instr(instruction, temp_addr);
                    if(temp.opcode == Opcode.BREAK) {
                        foundBreak = true;
                        addr_end = temp_addr;
                        mem_start = temp_addr + 4;
                    }
                    instrMap.put(temp_addr, temp);
                    //bw.write(instruction + "\t" + temp.output + "\n");
                    //System.out.print(instruction  + "\t" + temp.output + "\n");
                }
                temp_addr += 4;
            }
            br.close();
            //bw.close();
        } catch (Exception e) {
            System.out.println("The file was not found.");
        }
    }

    private static void run() {

        try{
            BufferedWriter bw = new BufferedWriter(new FileWriter("simulation.txt"));
            boolean breakFetched = false;
            while(!breakFetched) {
                String output = "--------------------\nCycle " + Integer.toString(cycle) + ":\n\n";

                breakFetched = myIF.process();
                myISSUE.process();
                myALU1.process();
                myALU2.process();
                myMEM.process();
                myWB.process();

                if (myIF.nextPreIssue.size() > 0)           preIssue.addAll(myIF.nextPreIssue);
                if (myISSUE.nextPreALU1.size() > 0)     preALU1.addAll(myISSUE.nextPreALU1);
                if (myISSUE.nextPreALU2.size() > 0)     preALU2.addAll(myISSUE.nextPreALU2);
                if (myALU1.nextPreMEM.size() > 0)       preMEM.addAll(myALU1.nextPreMEM);
                if (myALU2.nextPostALU2.size() > 0)     postALU2.addAll(myALU2.nextPostALU2);
                if(myMEM.nextPostMEM.size()>0)      postMEM.addAll(myMEM.nextPostMEM);

                output += myIF.output() + getPreIssue() + getPreALU1() + getPreMEM() + getPostMEM() + getPreALU2() + getPostALU2();
                output +=  "\n" + getRegs() + getData();
                //System.out.print(output);
                bw.write(output);
                cycle ++;
            }
            bw.close();
        } catch (Exception e){
           System.out.println(e);
        }
    }

    public static void main(String args[]){
        disassemble(args[0]);
        run();
    }
}

class Instr {
    int category, addr, mem_addr;
    int jumpTo,rs, rt, rd, imm, sa, offsetInt, base;
    int toWrite;
    String instr, output;
    Opcode opcode;
    Instr(String instr, Integer addr) throws Exception {
        this.instr = instr;
        this.opcode = getopcode(instr);
        this.category = categorize(instr);
        this.addr = addr;
        this.output = "["+ this.opcode;
        if (this.category == 1) {
           switch(this.opcode) {
                case J:
                    this.jumpTo = (Integer.parseInt(this.instr.substring(6),2) << 2);
                    this.output = String.format("%s #%d", this.output, this.jumpTo);
                    break;
                case JR:
                    this.rs = Integer.parseInt(instr.substring(6,11),2);
                    this.output = String.format("%s R%d", this.output, this.rs);
                    break;
                case BEQ:
                    this.rs = Integer.parseInt(instr.substring(6,11),2);
                    this.rt = Integer.parseInt(instr.substring(11,16),2);
                    this.offsetInt = getSignedNum(instr.substring(16)) << 2;
                    this.output = String.format("%s R%d, R%d, #%d", this.output, this.rs, this.rt, this.offsetInt);
                    break;
                case BLTZ:
                case BGTZ:
                    this.rs = Integer.parseInt(instr.substring(6,11),2);
                    this.offsetInt = getSignedNum(instr.substring(16)) << 2;
                    this.output = String.format("%s R%d, #%d", this.output, this.rs, this.offsetInt);
                    break;
                case BREAK: break;
                case SW:
                    this.base = Integer.parseInt(instr.substring(6,11),2);
                    this.rt = Integer.parseInt(instr.substring(11,16),2);
                    this.offsetInt = getSignedNum(instr.substring(16));
                    this.output = String.format("%s R%d, %d(R%d)", this.output, this.rt, this.offsetInt, this.base);
                    break;
                case LW:
                    this.base = Integer.parseInt(instr.substring(6,11),2);
                    this.rt = Integer.parseInt(instr.substring(11,16),2);
                    this.offsetInt = getSignedNum(instr.substring(16));
                    this.output = String.format("%s R%d, %d(R%d)", this.output, this.rt, this.offsetInt, this.base);
                    this.toWrite = this.rt;
                    break;
                case SLL:
                case SRL:
                case SRA:
                    this.rt = Integer.parseInt(instr.substring(11,16),2);
                    this.rd = Integer.parseInt(instr.substring(16,21),2);
                    this.sa = Integer.parseInt(instr.substring(21,26),2);
                    this.toWrite = this.rd;
                    this.output = String.format("%s R%d, R%d, #%d", this.output, this.rd, this.rt, this.sa);
                    break;
                // case NOP
            default: break;
            }
        } else if (this.category == 2) {
            this.rs = Integer.parseInt(instr.substring(6,11),2);
            this.rt = Integer.parseInt(instr.substring(11,16),2);
            if (Opcode.CATEGORY2_IMM.contains(this.opcode)) {
                // Category-2 instructions with source2 as immediate
                if (this.opcode == Opcode.ADDI){
                    this.imm = getSignedNum(instr.substring(16));
                } else {
                    this.imm = Integer.parseInt(instr.substring(16),2);
                }
                this.toWrite = this.rt;
                this.output = String.format("%s R%d, R%d, #%d", this.output, this.rt, this.rs, this.imm);
            } else {
                // Category-2 instructions with source2 as register
                this.rd = Integer.parseInt(instr.substring(16,21),2);
                this.toWrite = this.rd;
                this.output = String.format("%s R%d, R%d, R%d", this.output, this.rd, this.rs, this.rt);
            }
        }
        this.output += "]";
    }

    Opcode getopcode(String instr) throws Exception {
        String opcode = instr.substring(2,6);
        if(categorize(instr) == 1) {
            switch(opcode){
                case "0000": return Opcode.J;
                case "0001": return Opcode.JR;
                case "0010": return Opcode.BEQ;
                case "0011": return Opcode.BLTZ;
                case "0100": return Opcode.BGTZ;
                case "0101": return Opcode.BREAK;
                case "0110": return Opcode.SW;
                case "0111": return Opcode.LW;
                case "1000": return Opcode.SLL;
                case "1001": return Opcode.SRL;
                case "1010": return Opcode.SRA;
                case "1011": return Opcode.NOP;
                default: throw new IllegalArgumentException(instr);
            }
        } else if(categorize(instr) == 2) {
            switch(opcode){
                case "0000": return Opcode.ADD;
                case "0001": return Opcode.SUB;
                case "0010": return Opcode.MUL;
                case "0011": return Opcode.AND;
                case "0100": return Opcode.OR;
                case "0101": return Opcode.XOR;
                case "0110": return Opcode.NOR;
                case "0111": return Opcode.SLT;
                case "1000": return Opcode.ADDI;
                case "1001": return Opcode.ANDI;
                case "1010": return Opcode.ORI;
                case "1011": return Opcode.XORI;
                default: throw new IllegalArgumentException(instr);
            }
        } else {
            throw new IllegalArgumentException(instr);
        }
    }

    public static int categorize(String instr) {
        if(instr.startsWith("01")) {
            return 1;
        } else if (instr.startsWith("11")) {
            return 2;
        } else {
            return -1;
        }
    }

    public static int getSignedNum(String str) {
        if(str.charAt(0) == '0') return Integer.parseInt(str,2);
        str = str.replace('1','2');
        str = str.replace('0','1');
        str = str.replace('2','0');
        if(str.charAt(0) == '0') return -(Integer.parseInt(str,2)+1);
        return - Integer.parseInt(str,2);
    }
}

class IF {
    private static IF myIF ;
    private Instr waiting, executed;
    private boolean isStalled = false;
    public  LinkedList<Instr> nextPreIssue = new LinkedList<>();

    private IF () {}

    public static IF getIF () {
        if (myIF == null) myIF = new IF();
        return myIF;
    }

    public String output() {
        String res = "IF Unit:\n\tWaiting Instruction: ";
        res = waiting == null ? res + "\n": res + waiting.output + "\n";
        res += "\tExecuted Instruction:";
        res = executed == null? res + "\n": res + executed.output + "\n";
        return res;
    }

    public boolean process () {
        this.nextPreIssue = new LinkedList<>();
        if  (this.isStalled) {
            if(checkRegs(this.waiting)) {
                switch (waiting.opcode) {
                    case J   : MIPSsim.pc = this.waiting.jumpTo;                                                                                                                                                                                                       break;
                    case JR  : MIPSsim.pc = MIPSsim.regs[this.waiting.rs];                                                                                                                                                                                  break;
                    case BEQ : MIPSsim.pc = MIPSsim.regs[this.waiting.rs] == MIPSsim.regs[this.waiting.rt] ? MIPSsim.pc + this.waiting.offsetInt + 4 : MIPSsim.pc + 4;   break;
                    case BLTZ: MIPSsim.pc = MIPSsim.regs[this.waiting.rs] < 0 ? MIPSsim.pc + this.waiting.offsetInt : MIPSsim.pc + 4;                                                               break;
                    case BGTZ: MIPSsim.pc = MIPSsim.regs[this.waiting.rs] > 0 ? MIPSsim.pc + this.waiting.offsetInt : MIPSsim.pc + 4;                                                            break;
                }
                this.executed  = this.waiting;
                this.waiting = null;
                this.isStalled = false;
            }
            return false;
        } else {
            int num = 0;
            while (num < Math.min(2, 4 - MIPSsim.preIssue.size())) {
                Instr instr =  MIPSsim.instrMap.get(MIPSsim.pc);
                if(isBreak(instr)) {
                    this.waiting = null;
                    this.executed = instr;
                    return true;
                } else if(isBranch(instr)) {
                    // If registers are ready, then jump
                    if(checkRegs(instr)) {
                        switch (instr.opcode) {
                            case J   : MIPSsim.pc = instr.jumpTo;                                                                                                                                                                         break;
                            case JR  : MIPSsim.pc = MIPSsim.regs[instr.rs]; break;
                            case BEQ : MIPSsim.pc = MIPSsim.regs[instr.rs] == MIPSsim.regs[instr.rt] ? MIPSsim.pc + instr.offsetInt + 4 : MIPSsim.pc + 4; break;
                            case BLTZ: MIPSsim.pc = MIPSsim.regs[instr.rs] < 0 ? MIPSsim.pc + instr.offsetInt : MIPSsim.pc + 4; break;
                            case BGTZ: MIPSsim.pc = MIPSsim.regs[instr.rs] > 0 ? MIPSsim.pc + instr.offsetInt : MIPSsim.pc + 4; break;
                            default: break;
                        }
                        this.waiting = null;
                        this.executed = instr;
                    } else {
                        this.isStalled = true;
                        this.waiting = instr;
                        this.executed = null;
                    }
                    break;
                } else {
                    // non-branch functions
                    if (instr.opcode != Opcode.NOP) this.nextPreIssue.add(instr);
                    this.waiting = null;
                    this.executed = null;
                    MIPSsim.pc += 4;
                    num ++;
                }
            }
            return false;
        }
    }

    //private void updatePC() { MIPSsim.pc += 4;}

    private boolean isBranch(Instr instr) {
        return Opcode.BRANCHES.contains(instr.opcode);
    }

    private boolean isBreak(Instr instr) {
        return instr.opcode == Opcode.BREAK;
    }

    private boolean checkRegs(Instr instr) {
        Set<Integer> preIssueToWrites = getPIWrites();
        switch(instr.opcode) {
            case J: return true;
            case JR:
            case BLTZ:
            case BGTZ:
                return !(MIPSsim.toBeWritten.contains(instr.rs) || preIssueToWrites.contains(instr.rs));
            case BEQ:
                return !(MIPSsim.toBeWritten.contains(instr.rs)|| MIPSsim.toBeWritten.contains(instr.rt)
                        || preIssueToWrites.contains(instr.rs) || preIssueToWrites.contains(instr.rt));
            default:
                System.out.println("[CHECK REGS] Not a branch fucntion!!");
                return false;
        }
    }

    private Set<Integer> getPIWrites() {
        ArrayList<Integer> writes = new ArrayList<>();
        for (Instr instr: MIPSsim.preIssue) {
            writes.add(instr.toWrite);
        }
        return new HashSet<>(writes);
    }
}

class ISSUE {
    private static ISSUE myISSUE = null;
    public LinkedList<Instr> nextPreALU1;
    public LinkedList<Instr> nextPreALU2;

    private ISSUE (){}
    public static ISSUE getISSUE () {
        if (myISSUE == null) myISSUE = new ISSUE();
        return myISSUE;
    }
    public void process () {
        int numChecked = 0;
        int numIssuedALU1 = 0;
        int numIssuedALU2 = 0;
        boolean prevStoreIssued = true;
        Set<Integer> earlierToRead = new HashSet<>();
        Set<Integer> earlierToWrite = new HashSet<>();
        this.nextPreALU1 = new LinkedList<>();
        this.nextPreALU2 = new LinkedList<>();


        int L = MIPSsim.preIssue.size();
        while ( numChecked < L ) {
            Instr instr = MIPSsim.preIssue.poll();
            numChecked ++;

            if (numIssuedALU1 + numIssuedALU2 == 2) {
                MIPSsim.preIssue.add(instr);
                continue;
            }

            switch(instr.opcode) {
                case SW  : //mem_map.put(regs[instr.base] + instr.offsetInt, regs[instr.rt]);
                    if ( numIssuedALU1  == 0
                          // check if pre-ALU1 has empty slot
                            && MIPSsim.preALU1.size() < 2
                          // check if there is any RAW, WAW hazard (issued, unfinished instructions)
                            && !(MIPSsim.toBeWritten.contains(instr.base) || MIPSsim.toBeWritten.contains(instr.rt))
                          // check if there is any RAW, WAW, WAR hazard (earlier not-issued instructions)
                            && !(earlierToWrite.contains(instr.base) || earlierToWrite.contains(instr.rt) ||  earlierToRead.contains(instr.rt))
                          // check if stores are issued in order
                            && prevStoreIssued
                    ) {
                        // Pass all check -> issue -> preALU1
                        this.nextPreALU1.add(instr);
                        MIPSsim.toBeRead.add(instr.base);
                        MIPSsim.toBeRead.add(instr.rt);
                        numIssuedALU1 ++;
                    } else {
                        prevStoreIssued = false;
                        MIPSsim.preIssue.add(instr);
                    }
                    earlierToRead.add(instr.base);
                    earlierToRead.add(instr.rt);
                    break;
                case LW  : //regs[instr.rt] = mem_map.getOrDefault(regs[instr.base] + instr.offsetInt, 0);
                    if ( numIssuedALU1 == 0
                          // check if pre-ALU1 has empty slot
                            && MIPSsim.preALU1.size() < 2
                          // check if there is any RAW, WAW hazard (issued, unfinished instructions)
                            && !(MIPSsim.toBeWritten.contains(instr.base) || MIPSsim.toBeWritten.contains(instr.rt))
                          // check if there is any RAW, WAW, WAR hazard (earlier not-issued instructions)
                            && !(earlierToWrite.contains(instr.base) || earlierToWrite.contains(instr.rt) ||  earlierToRead.contains(instr.rt))
                        // check if stores are issued in order
                            && prevStoreIssued
                    ){
                    // Pass all check -> issue -> preALU1
                        this.nextPreALU1.add(instr);
                        MIPSsim.toBeRead.add(instr.base);
                        MIPSsim.toBeWritten.add(instr.rt);
                        numIssuedALU1 ++;
                    } else {
                        prevStoreIssued = false;
                        MIPSsim.preIssue.add(instr);
                    }
                    earlierToRead.add(instr.base);
                    earlierToWrite.add(instr.rt);
                    break;
                case SLL : //regs[instr.rd] = regs[instr.rt] << instr.sa;
                case SRL : //regs[instr.rd] = regs[instr.rt] >>> instr.sa;
                case SRA : //regs[instr.rd] = regs[instr.rt] >> instr.sa;
                    if ( numIssuedALU2 == 0
                          // check if pre-ALU2 has empty slot
                            && MIPSsim.preALU2.size()  < 2
                          // check if there is any RAW, WAW hazard (issued, unfinished instructions)
                            && !(MIPSsim.toBeWritten.contains(instr.rt) || MIPSsim.toBeWritten.contains(instr.rd))
                          // check if there is any RAW, WAW, WAR hazard (earlier not-issued instructions)
                            && !(earlierToWrite.contains(instr.rt) || earlierToWrite.contains(instr.rd) || earlierToRead.contains(instr.rd))
                    ) {
                        // Pass all check
                        this.nextPreALU2.add(instr);
                        MIPSsim.toBeRead.add(instr.rt);
                        MIPSsim.toBeWritten.add(instr.rd);
                        numIssuedALU2 ++;
                    }  else {
                        MIPSsim.preIssue.add(instr);
                    }
                    earlierToRead.add(instr.rt);
                    earlierToWrite.add(instr.rd);
                    break;
                //case NOP:
                case ADD : //regs[instr.rd] = regs[instr.rs] + regs[instr.rt];
                case SUB : //regs[instr.rd] = regs[instr.rs] - regs[instr.rt];
                case MUL : //regs[instr.rd] = regs[instr.rs] * regs[instr.rt];
                case AND : //regs[instr.rd] = regs[instr.rs] & regs[instr.rt];
                case OR  : //regs[instr.rd] = regs[instr.rs] | regs[instr.rt];
                case XOR ://regs[instr.rd] = regs[instr.rs] ^ regs[instr.rt];
                case NOR : //regs[instr.rd] = ~(regs[instr.rs] | regs[instr.rt]);
                case SLT : //regs[instr.rd] = regs[instr.rs] < regs[instr.rt] ? 1 : 0;
                    if (numIssuedALU2  == 0
                        // check if pre-ALU2 has enough empty slots
                        && MIPSsim.preALU2.size() < 2
                        // check if there is any RAW, WAW hazard (issued, unfinished instructions)
                        && !(MIPSsim.toBeWritten.contains(instr.rs) || MIPSsim.toBeWritten.contains(instr.rt)|| MIPSsim.toBeWritten.contains(instr.rd))
                        // check if there is any RAW, WAW, WAR hazard (earlier not-issued instructions)
                        && !(earlierToWrite.contains(instr.rt) || earlierToWrite.contains(instr.rs)
                            || earlierToWrite.contains(instr.rd) || earlierToRead.contains(instr.rd))
                    ) {
                        // Pass all check
                        this.nextPreALU2.add(instr);
                        MIPSsim.toBeRead.add(instr.rs);
                        MIPSsim.toBeRead.add(instr.rt);
                        MIPSsim.toBeWritten.add(instr.rd);
                        numIssuedALU2 ++;
                    } else {
                            MIPSsim.preIssue.add(instr);
                    }
                    earlierToRead.add(instr.rs);
                    earlierToRead.add(instr.rt);
                    earlierToWrite.add(instr.rd);
                    break;
                case ANDI: //regs[instr.rt] = regs[instr.rs] & instr.imm;
                case ORI : //regs[instr.rt] = regs[instr.rs] | instr.imm;
                case XORI: //regs[instr.rt] = regs[instr.rs] ^ instr.imm;
                case ADDI:
                    if (numIssuedALU2  == 0
                        // check if pre-ALU2 has empty slot
                        && MIPSsim.preALU2.size() < 2
                        // check if there is any RAW, WAW hazard (issued, unfinished instructions)
                        && !(MIPSsim.toBeWritten.contains(instr.rs) || MIPSsim.toBeWritten.contains(instr.rt))
                        // check if there is any RAW, WAW, WAR hazard (earlier not-issued instructions)
                        && !(earlierToWrite.contains(instr.rt) || earlierToWrite.contains(instr.rs)
                            || earlierToWrite.contains(instr.rd) || earlierToRead.contains(instr.rd))
                    ) {
                    // Pass all check
                        this.nextPreALU2.add(instr);
                        MIPSsim.toBeRead.add(instr.rs);
                        MIPSsim.toBeWritten.add(instr.rt);
                        numIssuedALU2 ++;
                    } else {
                        MIPSsim.preIssue.add(instr);
                    }
                    earlierToRead.add(instr.rs);
                    earlierToWrite.add(instr.rt);
                    break;
                default:
                    System.out.println("[CHECK OPERANDS] Not a valid instruction" + instr.opcode);
            }
        }
    }
}

class ALU1 {
    public LinkedList<Instr> nextPreMEM;

    private static ALU1 myALU1;
    private ALU1 (){}

    public static ALU1 getALU1 () {
        if (myALU1 == null) myALU1 = new ALU1();
        return myALU1;
    }
    public void process () {
        this.nextPreMEM = new LinkedList<>();

        if (!MIPSsim.preALU1.isEmpty()) {
            this.nextPreMEM.add(MIPSsim.preALU1.poll());
        }
    }
}

class ALU2 {
    public LinkedList<Instr> nextPostALU2;

    private static ALU2 myALU2;
    private ALU2 (){}

    public static ALU2 getALU2 () {
        if (myALU2 == null) myALU2 = new ALU2();
        return myALU2;
    }
    public void process () {
        this.nextPostALU2 = new LinkedList<>();

        if (!MIPSsim.preALU2.isEmpty()) {
            this.nextPostALU2.add(MIPSsim.preALU2.poll());
        }
    }
}

class MEM {
    public  LinkedList<Instr> nextPostMEM;

    private static MEM myMEM = null;
    private MEM (){}

    public static MEM getMEM () {
        if (myMEM == null) myMEM = new MEM();
        return myMEM;
    }

    public void process () {
        this.nextPostMEM = new LinkedList<>();

        if (!MIPSsim.preMEM.isEmpty()) {
            Instr instr = MIPSsim.preMEM.poll();
            switch(instr.opcode) {
                case SW:
                    MIPSsim.mem_map.put(MIPSsim.regs[instr.base] + instr.offsetInt, MIPSsim.regs[instr.rt]);
                    MIPSsim.toBeRead.remove(instr.base);
                    MIPSsim.toBeRead.remove(instr.rt);
                    break;
                case LW:
                    this.nextPostMEM.add(instr);
                    break;
                default:
                    System.out.println("[MEM] Invalid instruction " + instr.opcode);
            }
        }
    }

}

class WB{
    private static WB myWB = null;

    private WB (){}

    public static WB getWB () {
        if (myWB == null) myWB = new WB();
        return myWB;
    }

    public void process () {

        if (!MIPSsim.postMEM.isEmpty()) {
            Instr instr = MIPSsim.postMEM.poll();
            //if(instr.opcode != Opcode.LW) System.out.println("[WB] Invalid instruction!" + instr.output);
            MIPSsim.regs[instr.rt] = MIPSsim.mem_map.getOrDefault(MIPSsim.regs[instr.base] + instr.offsetInt, 0);
            MIPSsim.toBeRead.remove(instr.base);
            MIPSsim.toBeWritten.remove(instr.rt);
        }
        if(!MIPSsim.postALU2.isEmpty()) {
            Instr instr = MIPSsim.postALU2.poll();
            switch (instr.opcode) {
                case SLL : MIPSsim.regs[instr.rd] = MIPSsim.regs[instr.rt] << instr.sa;                         break;
                case SRL : MIPSsim.regs[instr.rd] = MIPSsim.regs[instr.rt] >>> instr.sa;                        break;
                case SRA : MIPSsim.regs[instr.rd] = MIPSsim.regs[instr.rt] >> instr.sa;                         break;
                case ADD : MIPSsim.regs[instr.rd] = MIPSsim.regs[instr.rs] + MIPSsim.regs[instr.rt];            break;
                case SUB : MIPSsim.regs[instr.rd] = MIPSsim.regs[instr.rs] - MIPSsim.regs[instr.rt];            break;
                case MUL : MIPSsim.regs[instr.rd] = MIPSsim.regs[instr.rs] * MIPSsim.regs[instr.rt];            break;
                case AND : MIPSsim.regs[instr.rd] = MIPSsim.regs[instr.rs] & MIPSsim.regs[instr.rt];            break;
                case OR  : MIPSsim.regs[instr.rd] = MIPSsim.regs[instr.rs] | MIPSsim.regs[instr.rt];            break;
                case XOR : MIPSsim.regs[instr.rd] = MIPSsim.regs[instr.rs] ^ MIPSsim.regs[instr.rt];            break;
                case NOR : MIPSsim.regs[instr.rd] = ~(MIPSsim.regs[instr.rs] | MIPSsim.regs[instr.rt]);         break;
                case SLT : MIPSsim.regs[instr.rd] = MIPSsim.regs[instr.rs] < MIPSsim.regs[instr.rt] ? 1 : 0;    break;
                case ANDI: MIPSsim.regs[instr.rt] = MIPSsim.regs[instr.rs] & instr.imm;                         break;
                case ORI : MIPSsim.regs[instr.rt] = MIPSsim.regs[instr.rs] | instr.imm;                         break;
                case XORI: MIPSsim.regs[instr.rt] = MIPSsim.regs[instr.rs] ^ instr.imm;                         break;
                case ADDI:
                      //overflow detection
                      boolean a = instr.imm > 0 && (Integer.MAX_VALUE - instr.imm) > MIPSsim.regs[instr.rs];
                      boolean b = instr.imm < 0 && (instr.imm - Integer.MIN_VALUE) > -MIPSsim.regs[instr.rs];
                      if(a || b) MIPSsim.regs[instr.rt] = MIPSsim.regs[instr.rs] + instr.imm;
                      break;
                 default:
                    System.out.println("[WB] Invalid instruction:" + instr.opcode);
            }
            // Update  status of registers
            switch(instr.opcode) {
                case SLL :
                case SRL :
                case SRA :
                    MIPSsim.toBeRead.remove(instr.rt);
                    MIPSsim.toBeWritten.remove(instr.rd);
                    break;
                case ADD :
                case SUB :
                case MUL :
                case AND :
                case OR :
                case XOR :
                case NOR :
                case SLT :
                    MIPSsim.toBeRead.remove(instr.rs);
                    MIPSsim.toBeRead.remove(instr.rt);
                    MIPSsim.toBeWritten.remove(instr.rd);
                    break;
                case ANDI:
                case ORI:
                case XORI:
                case ADDI:
                    MIPSsim.toBeRead.remove(instr.rs);
                    MIPSsim.toBeWritten.remove(instr.rt);
                    break;
                default:
                    System.out.println("[WB] Invalid instruction: " + instr.opcode);
            }
        }

    }
}
