const API = import.meta.env.VITE_API_URL || 'http://localhost:8080';
export async function request(path, options={}) {
  const token = localStorage.getItem('wallet_token');
  const headers = {'Content-Type':'application/json', ...(options.headers||{})};
  if(token) headers.Authorization=`Bearer ${token}`;
  const res = await fetch(`${API}${path}`, {...options, headers});
  if(res.status===401 || res.status===403){ localStorage.removeItem('wallet_token'); localStorage.removeItem('wallet_user'); }
  const text=await res.text(); let data={}; try{data=text?JSON.parse(text):{}}catch{data={message:text};}
  if(!res.ok) throw new Error(data.message||data.error||'Request failed');
  return data;
}
export const authApi={login:(phone,password)=>request('/api/auth/login',{method:'POST',body:JSON.stringify({phone,password})}),register:(phone,password)=>request('/api/auth/register',{method:'POST',body:JSON.stringify({phone,password})})};
export const walletApi={balance:()=>request('/api/balance'),transactions:()=>request('/api/transactions'),transfer:(to,amount)=>request('/api/p2p/transfer',{method:'POST',body:JSON.stringify({to,amount})}),onramp:(provider,amount)=>request('/api/onramp/complete',{method:'POST',body:JSON.stringify({provider,amount})})};
