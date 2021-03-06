MACHINE controller 
SETS
    PC = {read, compute, write};
    LPC = {lread, ldecide, lwrite}

VARIABLES
        fmiLevel,
        fmiPump,
        level ,
        time  ,
        pump ,
        pc   ,
        lpc  

CONSTANTS 
   maxlevel, minlevel, ReadDelay, WriteDelay, ComputeDelay

PROPERTIES
maxlevel:NATURAL & minlevel:NATURAL &
  /* maxlevel = 3000 & minlevel = 2000 &*/  ReadDelay = 20 & WriteDelay= 20 &  ComputeDelay = 100          

INVARIANT
         time : 0..12000000 &
         pump : BOOL &
         level : 0..200000 &
         fmiLevel : NATURAL &
         fmiPump : BOOL &
         pc : PC &
         lpc : LPC 

INITIALISATION 
                time :=  0 ||
                level :=  0  ||
                pump :=  FALSE  ||
                fmiLevel :: NATURAL  ||
                fmiPump :: BOOL  ||
                pc :=  read  ||
                lpc :=  lread 

OPERATIONS


    fmiReadInputs(l) =
            PRE   l : NATURAL /*&
                  pc = read */
            THEN
                  fmiLevel :=  l ||
                  pc :=  compute 
            END;

    fmiWriteOutputs =
    PRE
         pc = write 
    THEN
         fmiPump :=  pump ||
         pc :=  read 
    END;        

    switchOn = 
        PRE
            level <= minlevel /*& 
            pc = compute &
            lpc = ldecide */
        THEN
               pump :=  FALSE ||
               time :=  time + ComputeDelay ||
               lpc :=  lwrite 
        END;

    switchOff =
        PRE
            level >= maxlevel/* &
            pc = compute & 
            lpc = ldecide*/
        THEN
          pump :=  TRUE ||
          time :=  time + ComputeDelay ||
          lpc :=  lwrite 
        END;

    switchKeep =
        PRE
            level > minlevel & 
            level < maxlevel /*&
            pc = compute & 
            lpc = ldecide*/
        THEN
          time :=  time + ComputeDelay ||
          lpc :=  lwrite 
        END;



    readLevel = 
        PRE
             /* lpc = lread &*/
              pc = compute 
        THEN
              level :=  fmiLevel ||
              time :=  time + ReadDelay ||
              lpc :=  ldecide 
        END;

    writePump = 
        /*PRE
               pc = compute  &
               lpc = lwrite 
        THEN*/
             time :=  time + WriteDelay ||
             pc :=  write ||
             lpc :=  lread 
        /*END*/

    END
