#!/bin/bash

SCRIPT_PATH=rlexperiment.sh

DEFAULT_DIR="experiments"
#DEFAULT_BIN_PREFIX="binWeights"
#DEFAULT_HUMAN_PREFIX="humanWeights"

# Argument Parser
POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -n|--number-experiments)
    N_EXPERIMENTS="$2"
    shift # past argument
    shift # past value
    ;;
    -bs|--batch-size)
    BATCH_SIZE="$2"
    shift # past argument
    shift # past value
    ;;
    -d|--directory)
    DIR="$2"
    shift # past argument
    shift # past value
    ;;
    -c|--config)
    CONFIG="$2"
    shift # past argument
    shift # past value
    ;;
    -b|--save-bin)
    SAVE_BIN="$2"
    shift # past argument
    shift # past value
    ;;
    -bi|--bin-input)
    BIN_INPUT_1="$2"
    shift # past argument
    shift # past value
    ;;
    *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

# Check if the number of repetitions was set
if [ -z "$N_EXPERIMENTS" ]
then
    echo "Error: Number of repetitions not set. Use -n to set the number of repetitions."
    exit 1
fi

# Check if the directory was set
if [ -z "$DIR" ]
then
    echo "Warning: Directory not set. Using default directory \"$DEFAULT_DIR\". Use -d to set the directory."
    DIR=$DEFAULT_DIR
fi

# Check if the batch size was set
if [ -z "$BATCH_SIZE" ]
then
    echo "Batch size not set. Experiments will be run serially."
    BATCH_SIZE=1
fi

echo "Creating directories..."
mkdir -p "$DIR"
for i in $(seq 0 $(($N_EXPERIMENTS-1))); do
    mkdir -p "$DIR"/rep_"$i"
done
echo "Done."

echo "Lauching $N_EXPERIMENTS experiment(s)..."
for i in $(seq 0 $(($N_EXPERIMENTS-1))); do
    ((j=j%"$BATCH_SIZE")); ((j++==0)) && wait
    WORKING_DIR="rep_$i"
    echo "Lauching experiment $i..."
    echo "$SCRIPT_PATH" -s1 $i -d1 "$DIR/$WORKING_DIR" -o "$DIR/$WORKING_DIR/results.txt" -b1 "$SAVE_BIN/$WORKING_DIR" "$BIN_INPUT_1" "$CONFIG" ## For debugging
	source "$SCRIPT_PATH" -s1 $i -d1 "$DIR/$WORKING_DIR" -o "$DIR/$WORKING_DIR/results.txt" -b1 "$SAVE_BIN/$WORKING_DIR" "$BIN_INPUT_1" "$CONFIG" > "$DIR"/"$WORKING_DIR"/"$WORKING_DIR".log &
done
wait
echo "Done."
