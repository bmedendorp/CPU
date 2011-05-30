package org.bmedendorp.cpu;

import org.tal.redstonechips.RedstoneChips;
import org.tal.redstonechips.circuit.CircuitLibrary;

/**
 *
 * @author Tal Eisenberg
 */
public class CPU extends CircuitLibrary 
{
    @Override
    public Class[] getCircuitClasses() 
    {
        return new Class[] { cpu8088.class };
    }
}