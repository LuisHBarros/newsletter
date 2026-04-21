FROM public.ecr.aws/lambda/provided:al2023

COPY bootstrap /var/runtime/bootstrap
RUN chmod +x /var/runtime/bootstrap

CMD ["bootstrap"]
