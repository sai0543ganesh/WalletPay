export default function Toast({type='success',message}){return message?<div className={`toast ${type}`}>{message}</div>:null}
