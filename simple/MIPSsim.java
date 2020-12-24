/* On my honor, I have neither given nor received unauthorized aid on this assignment */

import java.io.*;
import java.util.*;

public class MIPSsim {
    static class Instr {
        int category, addr, mem_addr,jumpTo, rs, rt, rd, imm, sa, offsetInt, base;
        String instr, instrCode, output;
        Instr(String instr, Integer addr) {
            this.instr = instr;
            this.instrCode = getInstrCode(instr);
            this.category = categorize(instr);
            this.addr = addr;
            this.output = Integer.toString(addr) + "\t" + this.instrCode;
            if (this.category == 1) {
               switch(this.instrCode) {
                    case "J":
                        this.jumpTo = (Integer.parseInt(this.instr.substring(6),2) << 2);
                        this.output = String.format("%s #%d", this.output, this.jumpTo);
                        break;
                    case "JR":
                        this.rs = Integer.parseInt(instr.substring(6,11),2);
                        this.output = String.format("%s R%d", this.output, this.rs);
                        break;
                    case "BEQ":
                        this.rs = Integer.parseInt(instr.substring(6,11),2);
                        this.rt = Integer.parseInt(instr.substring(11,16),2);
                        this.offsetInt = getSignedNum(instr.substring(16)) << 2;
                        this.output = String.format("%s R%d, R%d, #%d", this.output, this.rs, this.rt, this.offsetInt);
                        break;
                    case "BLTZ":
                    case "BGTZ":
                        this.rs = Integer.parseInt(instr.substring(6,11),2);
                        this.offsetInt = getSignedNum(instr.substring(16)) << 2;
                        this.output = String.format("%s R%d, #%d", this.output, this.rs, this.offsetInt);
                        break;
                    case "BREAK": break;
                    case "SW":
                    case "LW":
                        this.base = Integer.parseInt(instr.substring(6,11),2);
                        this.rt = Integer.parseInt(instr.substring(11,16),2);
                        this.offsetInt = getSignedNum(instr.substring(16));
                        this.output = String.format("%s R%d, %d(R%d)", this.output, this.rt, this.offsetInt, this.base);
                        break;
                    case "SLL":
                    case "SRL":
                    case "SRA":
                        this.rt = Integer.parseInt(instr.substring(11,16),2);
                        this.rd = Integer.parseInt(instr.substring(16,21),2);
                        this.sa = Integer.parseInt(instr.substring(21,26),2);
                        this.output = String.format("%s R%d, R%d, #%d", this.output, this.rd, this.rt, this.sa);
                        break;
                    // case "NOP"
                default: break;
                }
            } else if (this.category == 2) {
                this.rs = Integer.parseInt(instr.substring(6,11),2);
                this.rt = Integer.parseInt(instr.substring(11,16),2);
                if (category2Imm.contains(this.instrCode)) {
                    // Category-2 instructions with source2 as immediate
                    if (this.instrCode == "ADDI"){
                        this.imm = getSignedNum(instr.substring(16));
                    } else {
                        this.imm = Integer.parseInt(instr.substring(16),2);
                    }
                    this.output = String.format("%s R%d, R%d, #%d", this.output, this.rt, this.rs, this.imm);                } else {
                    // Category-2 instructions with source2 as register
                    this.rd = Integer.parseInt(instr.substring(16,21),2);
                    this.output = String.format("%s R%d, R%d, R%d", this.output, this.rd, this.rs, this.rt);
                }
            }
        }
    }
    private static final Set<String> category2Imm = new HashSet<String>(Arrays.asList("ADDI", "ANDI", "ORI", "XORI"));
    private static int[] regs = new int[32];
    private static HashMap<Integer, Instr> instr_map = new HashMap<Integer, Instr>();
    private static HashMap<Integer, Integer> mem_map = new HashMap<Integer, Integer>();
    private static int addr_start = 256;
    private static int addr = 256;
    private static int addr_end;
    private static int cycle = 1;
    private static int mem_start, mem_end;

    private static int categorize(String instr) {
        if(instr.startsWith("01")) {
            return 1;
        } else if (instr.startsWith("11")) {
            return 2;
        } else {
            return -1;
        }
    }
    private static int getSignedNum(String str) {
        if(str.charAt(0) == '0') return Integer.parseInt(str,2);
        boolean flag = str.charAt(0) == '0';
        str = str.replace('1','2');
        str = str.replace('0','1');
        str = str.replace('2','0');
        if(str.charAt(0) == '0') return -(Integer.parseInt(str,2)+1);
        return - Integer.parseInt(str,2);
    }
    private static String getInstrCode(String instr) {
        String opcode = instr.substring(2,6);
        if(categorize(instr) == 1) {
            switch(opcode){
                case "0000": return "J";
                case "0001": return "JR";
                case "0010": return "BEQ";
                case "0011": return "BLTZ";
                case "0100": return "BGTZ";
                case "0101": return "BREAK";
                case "0110": return "SW";
                case "0111": return "LW";
                case "1000": return "SLL";
                case "1001": return "SRL";
                case "1010": return "SRA";
                case "1011": return "NOP";
                default: return "Invalid";
            }
        } else if(categorize(instr) == 2) {
            switch(opcode){
                case "0000": return "ADD";
                case "0001": return "SUB";
                case "0010": return "MUL";
                case "0011": return "AND";
                case "0100": return "OR";
                case "0101": return "XOR";
                case "0110": return "NOR";
                case "0111": return "SLT";
                case "1000": return "ADDI";
                case "1001": return "ANDI";
                case "1010": return "ORI";
                case "1011": return "XORI";
                default: return "Invalid";
            }
        } else {
            System.out.println("Invalid instruction: " + instr);
            return "Invalid";
        }
    }
    private static String getRegister(){
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
        return res + "\n";
    }
    private static void preparing(String fileName) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            BufferedWriter bw = new BufferedWriter(new FileWriter("disassembly.txt"));
            String instruction;
            boolean foundBreak = false;
            int temp_addr = 256;
            while((instruction = br.readLine()) != null) {
                if (foundBreak) {
                    int num = getSignedNum(instruction);
                    mem_map.put(temp_addr, num);
                    mem_end = temp_addr;
                    bw.write(instruction + "\t" + temp_addr + "\t" + Integer.toString(num) + "\n");
                } else {
                    Instr temp = new Instr(instruction, temp_addr);
                    if(temp.instrCode == "BREAK") {
                        foundBreak = true;
                        addr_end = temp_addr;
                        mem_start = temp_addr + 4;
                    }
                    instr_map.put(temp_addr, temp);
                    bw.write(instruction + "\t" + temp.output + "\n");
                }
                temp_addr += 4;
            }
            br.close();
            bw.close();
        } catch (Exception e) {
            System.out.println("The file was not found.");
        }
    }
    private static void executing() {
        Set<String> jumps = new HashSet<String>(Arrays.asList("J", "JR"));
        try{
            BufferedWriter bw = new BufferedWriter(new FileWriter("simulation.txt"));
            while(instr_map.containsKey(addr)) {
                //if(cycle > 1000) break;
                Instr instr = instr_map.get(addr);
                String header = "--------------------\n" + String.format("Cycle %d:\t%s\n\n",cycle, instr.output);
                switch(instr.instrCode){
                    case "J"   : addr = instr.jumpTo;                                                            break;
                    case "JR"  : addr = regs[instr.rs];                                                          break;
                    case "BEQ" : addr = regs[instr.rs] == regs[instr.rt] ? addr + instr.offsetInt : addr;        break;
                    case "BLTZ": addr = regs[instr.rs] < 0 ? addr + instr.offsetInt : addr;                      break;
                    case "BGTZ": addr = regs[instr.rs] > 0 ? addr + instr.offsetInt : addr;                      break;
                    //case "BREAK" :
                    case "SW"  : mem_map.put(regs[instr.base] + instr.offsetInt, regs[instr.rt]);                break;
                    case "LW"  : regs[instr.rt] = mem_map.getOrDefault(regs[instr.base] + instr.offsetInt, 0);   break;
                    case "SLL" : regs[instr.rd] = regs[instr.rt] << instr.sa;                                    break;
                    case "SRL" : regs[instr.rd] = regs[instr.rt] >>> instr.sa;                                   break;
                    case "SRA" : regs[instr.rd] = regs[instr.rt] >> instr.sa;                                    break;
                    //case "NOP":
                    case "ADD" : regs[instr.rd] = regs[instr.rs] + regs[instr.rt];                               break;
                    case "SUB" : regs[instr.rd] = regs[instr.rs] - regs[instr.rt];                               break;
                    case "MUL" : regs[instr.rd] = regs[instr.rs] * regs[instr.rt];                               break;
                    case "AND" : regs[instr.rd] = regs[instr.rs] & regs[instr.rt];                               break;
                    case "OR"  : regs[instr.rd] = regs[instr.rs] | regs[instr.rt];                               break;
                    case "XOR" : regs[instr.rd] = regs[instr.rs] ^ regs[instr.rt];                               break;
                    case "NOR" : regs[instr.rd] = ~(regs[instr.rs] | regs[instr.rt]);                            break;
                    case "SLT" : regs[instr.rd] = regs[instr.rs] < regs[instr.rt] ? 1 : 0;                       break;
                    case "ANDI": regs[instr.rt] = regs[instr.rs] & instr.imm;                                    break;
                    case "ORI" : regs[instr.rt] = regs[instr.rs] | instr.imm;                                    break;
                    case "XORI": regs[instr.rt] = regs[instr.rs] ^ instr.imm;                                    break;
                    case "ADDI":
                        //overflow detection
                        boolean a = instr.imm > 0 && (Integer.MAX_VALUE - instr.imm) > regs[instr.rs];
                        boolean b = instr.imm < 0 && (instr.imm - Integer.MIN_VALUE) > -regs[instr.rs];
                        if(a || b) regs[instr.rt] = regs[instr.rs] + instr.imm;
                        break;
                    default:                                                                                     break;
                }
                //System.out.println(header + getRegister() + getData());
                bw.write(header);
                bw.write(getRegister());
                bw.write(getData());

                if (!jumps.contains(instr.instrCode)) addr += 4;
                if (instr.instrCode == "BREAK" || addr < addr_start || addr > addr_end) {
                    //System.out.printf("addr:%d, start:%d, end:%d\n", addr, addr_start, addr_end);
                    bw.close();
                    break;
                }
                cycle += 1;
            }
        } catch (IOException e){
            //System.out.println("Something went wrong during executing... .");
            System.out.println(e);
        }
    }

    public static void main(String args[]){
        preparing(args[0]);
        executing();
    }
}


