import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {walletApi} from '../api/client';
import {Landmark,CheckCircle2,LoaderCircle} from 'lucide-react';

const money=v=>new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:2}).format(v/100);

export default function AddMoney(){
  const [amount,setAmount]=useState('');
  const [provider,setProvider]=useState('HDFC Bank');
  const [status,setStatus]=useState(null);
  const [busy,setBusy]=useState(false);
  const nav=useNavigate();

  async function submit(e){
    e.preventDefault();
    setStatus(null);
    const paise=Math.round(Number(amount)*100);
    if(!paise||paise<100) return setStatus({type:'error',message:'Enter at least ₹1.'});
    setBusy(true);
    try{
      const balance=await walletApi.onramp(provider,paise);
      setStatus({type:'success',message:`₹${Number(amount).toLocaleString('en-IN')} added successfully.`,balance});
      setAmount('');
    }catch(e){setStatus({type:'error',message:e.message})}
    finally{setBusy(false)}
  }

  return <div className="page narrow"><div className="page-title-row"><div><h1>Add money</h1><p>Fund your wallet and see your updated balance immediately.</p></div></div><section className="panel form-panel">{status&&<div className={`notice ${status.type}`}>{status.type==='success'?<CheckCircle2/>:null}<div><b>{status.message}</b>{status.balance&&<p className="small-note">Current available balance: <b>{money(status.balance.amount)}</b></p>}</div></div>}<form onSubmit={submit}><label>Payment provider<select value={provider} onChange={e=>setProvider(e.target.value)}><option>HDFC Bank</option><option>ICICI Bank</option><option>State Bank of India</option></select></label><label>Amount (₹)<input type="number" min="1" step="1" value={amount} onChange={e=>setAmount(e.target.value)} placeholder="Enter amount"/></label><button className="primary-btn full" disabled={busy}>{busy?<><LoaderCircle className="spin"/> Processing…</>:<><Landmark/> Add money</>}</button></form>{status?.type==='success'&&<button className="secondary-btn full" onClick={()=>nav('/dashboard')}>View updated dashboard</button>}<div className="info-box">Demo mode: the bank confirmation is simulated safely by the backend, so successful top-ups immediately update your real wallet balance and transaction history.</div></section></div>
}
