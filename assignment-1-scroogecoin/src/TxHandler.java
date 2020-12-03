import java.util.ArrayList;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = utxoPool;
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        ArrayList<Transaction.Input> inputs =  tx.getInputs();
        ArrayList<Transaction.Output> outputs =  tx.getOutputs();
        ArrayList<UTXO> processedUtxo = new ArrayList();
        double totalInputVals = 0;
        double totalOutputVals = 0;

        for (int i = 0; i < inputs.size(); i++) {
            Transaction.Input in = inputs.get(i);
            UTXO lastUtxo = new UTXO(in.prevTxHash, in.outputIndex);
            Transaction.Output prevTx = utxoPool.getTxOutput(lastUtxo);

            /** (1) all outputs claimed by {@code tx} are in the current UTXO pool */
            if (!utxoPool.contains(lastUtxo)) {
                return false;
            }

            /** (2) the signatures on each input of {@code tx} are valid */
            if(in.signature == null || !Crypto.verifySignature(prevTx.address, tx.getRawDataToSign(i), in.signature)) {
                return false;
            }

            /** (3) no UTXO is claimed multiple times by {@code tx} */
            if(processedUtxo.contains(lastUtxo)) {
                return false;
            }

            processedUtxo.add(lastUtxo);
            totalInputVals += prevTx.value;
        }

        /** (4) all of {@code tx}s output values are non-negative */
        for (int i = 0; i < outputs.size(); i++) {
            Transaction.Output op = outputs.get(i);
            if (op.value < 0) {
                return false;
            }

            totalOutputVals += op.value;
        }

        return (totalInputVals >= totalOutputVals);
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> result = new ArrayList();

        for (Transaction tx : possibleTxs) {
            if (!isValidTx(tx)) {
                continue;
            }

            result.add(tx);
            ArrayList<Transaction.Input> inputs =  tx.getInputs();
            ArrayList<Transaction.Output> outputs =  tx.getOutputs();

            /** Add valid UTXO */
            for (int i = 0; i < outputs.size(); i++) {
                Transaction.Output output = outputs.get(i);
                UTXO utxo = new UTXO(tx.getHash(), i);
                utxoPool.addUTXO(utxo, output);
            }

            /** Remove consumed UTXO */
            for (int i = 0; i < inputs.size(); i++) {
                Transaction.Input input = inputs.get(i);
                UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                utxoPool.removeUTXO(utxo);
            }
        }

        return result.toArray(new Transaction[0]);
    }
}
