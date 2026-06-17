  ### 1. Puertos de tu App (Spring Boot)                                                            
                                                                                                    
  •  8080  (TCP): Es el puerto por defecto de Spring Boot. Aquí vivirá el API principal de tu nuevo 
  proyecto  dvrs  (Miras Monitor).                                                                  
                                                                                                    
  ### 2. Puertos de Bases de Datos (Docker)                                                         
                                                                                                    
  •  5432  (TCP): Ocupado por PostgreSQL.                                                           
  •  6379  (TCP): Ocupado por Redis.                                                                
                                                                                                    
  ### 3. Puertos del Gestor de Cámaras (WVP-Pro en Docker)                                          
                                                                                                    
  (Nota: WVP-Pro usa la red del host directamente)                                                  
                                                                                                    
  •  18080  (TCP): Interfaz Web y API de WVP-Pro.                                                   
  •  5060  (UDP / TCP): Puerto SIP. Este es crucial porque es por donde WVP-Pro se comunica         
  (señalización) con los DVRs y cámaras que soportan el protocolo GB28181.
  
  ### 4. Puertos del Servidor de Video (ZLMediaKit en Docker)
  
  (Nota: ZLMediaKit también usa la red del host directamente)
  
  •  80  (TCP): Puerto HTTP principal. Lo usa para recibir los Webhooks y también para servir video 
  en formatos como HTTP-FLV o HLS.
  •  554  (TCP / UDP): Puerto estándar para transmisión RTSP.
  •  1935  (TCP): Puerto estándar para transmisión RTMP.
  •  10000  (UDP / TCP): Puerto Proxy RTP. Usado internamente para la comunicación de WVP.          
  •  30000  al  30500  (UDP / TCP): Rango de puertos dinámicos. Por aquí es por donde literalmente  
  entra el flujo de video (RTP) de las cámaras hacia el servidor cuando empieza a transmitir.      
