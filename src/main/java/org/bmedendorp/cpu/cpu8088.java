package org.bmedendorp.cpu;

import java.util.LinkedList;
import java.util.Queue;

import org.bukkit.command.CommandSender;

import org.tal.redstonechips.circuit.Circuit;
import org.tal.redstonechips.util.BitSet7;
import org.tal.redstonechips.util.BitSetUtils;

/**
 *
 * @author Brian Medendorp
 */
public class cpu8088 extends Circuit 
{
	protected class InstructionParameters
	{
		BitSet7 sourceSegment;
		BitSet7 sourceOffset;
		BitSet7 destSegment;
		BitSet7 destOffset;
		int sourceRegister;
		int destinationRegister;
		BitSet7 constant;
	}
	
	protected interface InstructionInterface
	{
		boolean update(InstructionParameters params);
	}
	
	protected InstructionInterface currentInstruction;
	protected InstructionParameters parameters = new InstructionParameters();
	
	protected String mode;		// min/max
	protected int cycle;		// t1/t2/t3/t4/tw/ti
	protected Queue<BitSet7> instructionQueue = new LinkedList<BitSet7>();
	
	// Internal Registers
	protected BitSet7 ax = new BitSet7(16);
	protected BitSet7 bx = new BitSet7(16);
	protected BitSet7 cx = new BitSet7(16);
	protected BitSet7 dx = new BitSet7(16);
	protected BitSet7 sp = new BitSet7(16);
	protected BitSet7 bp = new BitSet7(16);
	protected BitSet7 si = new BitSet7(16);
	protected BitSet7 di = new BitSet7(16);
	protected BitSet7 es = new BitSet7(16);
	protected BitSet7 cs = new BitSet7(16);
	protected BitSet7 ss = new BitSet7(16);
	protected BitSet7 ds = new BitSet7(16);
	protected BitSet7 ip = new BitSet7(16);
	protected BitSet7 flags = new BitSet7(16);

	// Input Pin Assignments
	public static final int pinDataIn = 0;
	public static final int pinClk = 8;
	public static final int pinReset = 9;
	public static final int pinReady = 10;
	
	// Output Pin Assignments
	public static final int pinAddressBit0 = 0;
	public static final int pinAddressBit7 = pinAddressBit0 + 8;
	public static final int pinAddressBit16 = pinAddressBit0 + 16;
	public static final int pinDataOut = pinAddressBit0;
	public static final int pinRD = 20;
	public static final int pinWR = 21;
	public static final int pinALE = 22;
	
	// Cycle Constants
	protected static final int t1 = 0;
	protected static final int t2 = 1;
	protected static final int t3 = 2;
	protected static final int t4 = 3;
	
	public static final BitSet7 clearBitSet = new BitSet7();
	
	@Override
    public void inputChange(int inIdx, boolean newLevel) 
    {
		// Reset if requested
		if (inIdx == pinReset && newLevel)
		{
			reset();
			return;
		}
		
		// Advance operation on clock signal
		if (inIdx == pinClk && newLevel)
		{
			if ( currentInstruction.update(parameters) )
			{
				cycle++;
				if (cycle > t4)
					cycle = t1;
			}
		}
    }

    @Override
    protected boolean init(CommandSender sender, String[] args) 
    {
        if (args.length < 1)
        {
        	// Default to 'min' mode
        	mode = "min";
        }
        else if (args.length > 1)
        {
        	error(sender, "Unexpected number of arguments");
        	return false;
        }
        else
        {        
        	args[0].trim();
        	args[0].toLowerCase();
        	if (args[0].equals("max"))
        	{
        		mode = "max";	// Max Mode - Used in conjunction with a bus controller
        	}
        	else if (args[0].equals("min"))
        	{
        		mode = "min";	// Min Mode - Standalone
        	}
        	else
        	{
        		error(sender, "Invalid mode argument");
        		return false;
        	}
        }
        
        // For now, we're going to hardwire the chip for 11 inputs and 23 outputs
        // Will want to revisit this as the code becomes more functional
        if (outputs.length != 23 || inputs.length != 11)
        {
        	error(sender, "Expecting 11 inputs and 23 outputs");
        	return false;
        }
        
        reset();
        return true;
    }
    
    // Reset
    protected void reset()
    {
    	currentInstruction = new InstructionFetchNextInstruction();
    	cycle = t1;
    	
    	// Set default output states
    	sendOutput(pinRD, true);
    	sendOutput(pinWR, true);
    	sendOutput(pinALE, false);
    	
    	// Clear the instruction queue
    	instructionQueue.removeAll(instructionQueue);
    	
    	// Boot Address
    	cs = BitSetUtils.intToBitSet(0xFFFF, 16);
    	ip = BitSetUtils.intToBitSet(0x0000, 16);
    }

    // Read the current status of the data bits (input)
    protected BitSet7 getDataBits()
    {
    	return getInputBits().get(pinDataIn, pinDataIn + 8);
    }
    
    // Set the data bits (output)
    protected void setDataBits(BitSet7 data)
    {
    	sendBitSet(pinDataOut, 8, data);
    }
    	
    protected BitSet7 createFullAddress(BitSet7 segment, BitSet7 offset)
    {
    	BitSet7 address = (BitSet7)segment.clone();
    	BitSetUtils.shiftLeft(address, 16);
    	BitSetUtils.shiftLeft(address, 17);
    	BitSetUtils.shiftLeft(address, 18);
    	BitSetUtils.shiftLeft(address, 19);
    	address = addBitSets(address, offset, 20);
    	return address;
    }
    
    // Borrowed from Eisental's adder circuit
    public static BitSet7 addBitSets(BitSet7 aSet, BitSet7 bSet, int length) {
        BitSet7 s = (BitSet7)aSet.clone();
        s.xor(bSet);
        BitSet7 c = (BitSet7)aSet.clone();
        c.and(bSet);

        while (!c.isEmpty()) {
            BitSetUtils.shiftLeft(c, length);
            BitSet7 oldS = (BitSet7)s.clone();
            s.xor(c);
            c.and(oldS);
        }

        if (s.size() > length)
        	s.clear(length, s.size());

        return s;
    }
    
    public boolean isSet(int inIdx)
    {
    	BitSet7 inputs = getInputBits();
    	return inputs.get(inIdx);
    }
    
    public static BitSet7 incrementBitSet(BitSet7 bits, int length)
    {
    	BitSet7 one = new BitSet7();
    	one.set(0);
    	return addBitSets(bits, one, length);
    }
    
    class InstructionFetchNextInstruction implements InstructionInterface
    {
    	public boolean update(InstructionParameters params)
    	{
			switch (cycle)
			{
			case t1:
				// Place the instruction address on the bus
				BitSet7 address = createFullAddress(cs, ip);
				sendBitSet(pinAddressBit0, 20, address);
				sendOutput(pinALE, true);
				
				return true;
			case t2:
				// Clear the data bits (lower 8 address bits) to allow release the bus
				sendOutput(pinALE, false);
				sendBitSet(pinDataOut, 8, clearBitSet);
			
				// Send the read signal
				sendOutput(pinRD, false);
					
				return true;
			case t3:
				// Check the status of the READY signal
				if ( isSet(pinReady) )
				{
					// Read the instruction from the data bus
					instructionQueue.offer(getDataBits());
					
					// Reset the read signal
					sendOutput(pinRD, true);
					
					return true;
				}
				// We'll sit in a wait state until our data is ready
				return false;
			case t4:
				// Increment our instruction pointer
				ip = incrementBitSet(ip, 16);
				
				// Write our instruction to memory
				ds = (BitSet7)clearBitSet.clone();
				di = (BitSet7)clearBitSet.clone();
				parameters.constant = instructionQueue.poll();
				parameters.destSegment = ds;
				parameters.destOffset = di;
				currentInstruction = new InstructionWriteMainMemory();
				
				return true;
			default:
				return false;
			}
		}
    }
    
    class InstructionWriteMainMemory implements InstructionInterface
    {
    	public boolean update(InstructionParameters params)
    	{
			switch (cycle)
			{
			case t1:
				// Place the address on the bus
				BitSet7 address = createFullAddress(params.destSegment, params.destOffset);
				sendBitSet(pinAddressBit0, 20, address);
				sendOutput(pinALE, true);
				
				return true;
			case t2:
				// Place the word to write on the data bus
				sendOutput(pinALE, false);
				sendBitSet(pinDataOut, 8, params.constant);
			
				// Send the write signal
				sendOutput(pinWR, false);
					
				return true;
			case t3:
				// Check the status of the READY signal
				if ( isSet(pinReady) )
				{
					// Reset the write signal and perform the actual write
					sendOutput(pinWR, true);
					
					return true;
				}
				// We'll sit in a wait state until our data is ready
				return false;
			case t4:
				// Get our next instruction
				currentInstruction = new InstructionFetchNextInstruction();
				return true;
			default:
				return false;
			}
		}
    }   
 }